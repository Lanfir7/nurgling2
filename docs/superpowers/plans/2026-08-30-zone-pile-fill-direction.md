# Zone Pile Fill Direction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a synchronized per-zone pile-fill direction, a four-arrow selector and selected-zone ground arrow, restore deterministic corner-first pile placement, and make duplicated zones follow the full new-zone persistence lifecycle.

**Architecture:** `PileFillDirection` is a routing property on `NArea`, serialized to JSON, merged through `AreaSnapshot`, stored in local `Areas.db` migration 7, and included in server `areas.data`. Zone coordinate pairs carry their owning area through a small `DirectedAreaBounds` subtype so existing actions retain direction without rewriting every pair-based automation API; non-zone rectangles default to legacy `LEFT_TO_RIGHT`. UI selection and rendering consume the same enum, preventing display/behavior drift.

**Tech Stack:** Java 8, Haven render tree/model API, org.json, SQLite JDBC, JUnit 5, Apache Ant.

**Spec:** `docs/superpowers/specs/2026-08-30-zone-pile-fill-direction-design.md`

## Global Constraints

- `LEFT_TO_RIGHT` must exactly preserve the pre-nearest-sort order: upper-left start, top-to-bottom within a column, then columns left-to-right.
- Missing, blank, or unknown persisted values must resolve to `LEFT_TO_RIGHT` without aborting zone load.
- Existing stockpiles are never moved; only the candidate order for a newly created pile changes.
- Preserve the current previous-stockpile escape handling in `PileMaker.approachAndPreserveEscape`.
- Direction changes belong to `AreaFieldGroup.ROUTING` and must use the existing dirty-save/debounce path.
- A duplicate preserves user data and direction but receives a new UUID, version/baseline zero, cleared sync metadata, and all four field groups dirty.
- The arrow is visible only for the selected zone while the zones window is open and must never affect clicking, collision, or pathfinding.
- Preserve all unrelated worktree changes. Before every commit, stage only this task's hunks with `git add -p` for already-modified files and inspect `git diff --cached`.

---

### Task 1: Direction model and unified new-zone lifecycle

**Files:**
- Create: `src/nurgling/areas/PileFillDirection.java`
- Create: `src/nurgling/areas/AreaCreation.java`
- Modify: `src/nurgling/areas/NArea.java:14-497, 781-836`
- Modify: `src/nurgling/NMapView.java:1310-1414`
- Create: `test/nurgling/areas/PileFillDirectionTest.java`
- Create: `test/nurgling/areas/AreaCreationTest.java`

**Interfaces:**
- Produces: `PileFillDirection.fromStored(Object): PileFillDirection`
- Produces: `NArea.pileFillDirection: PileFillDirection`
- Produces: `NArea.setPileFillDirection(PileFillDirection): boolean`; returns `true` only when the value changed and marks `ROUTING` dirty.
- Produces: `AreaCreation.initializeNew(NArea): void`
- Produces: `AreaCreation.duplicate(NArea, int, String): NArea`

- [ ] **Step 1: Write failing enum and JSON compatibility tests**

Create `PileFillDirectionTest` with these cases:

```java
package nurgling.areas;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PileFillDirectionTest {
    @Test void defaultsToLegacyOrder() {
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, new NArea("old").pileFillDirection);
    }

    @Test void jsonRoundTripPreservesDirection() {
        NArea area = new NArea("zone");
        area.id = 1;
        area.space = new NArea.Space();
        area.pileFillDirection = PileFillDirection.BOTTOM_TO_TOP;
        NArea restored = new NArea(new JSONObject(area.toJson().toString()));
        assertEquals(PileFillDirection.BOTTOM_TO_TOP, restored.pileFillDirection);
    }

    @Test void missingAndUnknownValuesUseLegacyOrder() {
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, PileFillDirection.fromStored(null));
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, PileFillDirection.fromStored(""));
        assertEquals(PileFillDirection.LEFT_TO_RIGHT, PileFillDirection.fromStored("future-value"));
    }

    @Test void setterMarksOnlyRoutingDirty() {
        NArea area = new NArea("zone");
        assertTrue(area.setPileFillDirection(PileFillDirection.RIGHT_TO_LEFT));
        assertEquals(java.util.EnumSet.of(AreaFieldGroup.ROUTING), area.dirtyGroups);
        assertFalse(area.setPileFillDirection(PileFillDirection.RIGHT_TO_LEFT));
    }
}
```

- [ ] **Step 2: Run tests and verify the model API is absent**

Run: `ant test`

Expected: test compilation fails because `PileFillDirection`, `NArea.pileFillDirection`, and `setPileFillDirection` do not exist.

- [ ] **Step 3: Implement the enum and NArea serialization**

Create the enum with stable uppercase wire values and a defensive parser:

```java
package nurgling.areas;

public enum PileFillDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP;

    public static PileFillDirection fromStored(Object value) {
        if (value == null) return LEFT_TO_RIGHT;
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) return LEFT_TO_RIGHT;
        try {
            return valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LEFT_TO_RIGHT;
        }
    }
}
```

In `NArea`, add `public static final String PILE_FILL_DIRECTION_JSON = "pile_fill_direction"`, initialize the field to `LEFT_TO_RIGHT`, parse it in `NArea(JSONObject)`, copy it in `updateFrom`, write `name()` in `toJson`, and implement the setter exactly as follows:

```java
public boolean setPileFillDirection(PileFillDirection direction) {
    PileFillDirection next = direction == null
            ? PileFillDirection.LEFT_TO_RIGHT : direction;
    if (pileFillDirection == next) return false;
    pileFillDirection = next;
    markDirty(AreaFieldGroup.ROUTING);
    return true;
}
```

- [ ] **Step 4: Run tests and verify direction compatibility passes**

Run: `ant test`

Expected: all tests pass, including the four new direction-model tests.

- [ ] **Step 5: Write failing lifecycle and duplicate tests**

Create `AreaCreationTest`:

```java
package nurgling.areas;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class AreaCreationTest {
    @Test void initializeNewResetsSyncIdentityAndMarksEveryGroup() {
        NArea area = new NArea("zone");
        area.uuid = "old";
        area.version = 9;
        area.baselineVersion = 9;
        area.synced = true;
        area.lastUpdated = 123L;

        AreaCreation.initializeNew(area);

        assertNotNull(area.uuid);
        assertNotEquals("old", area.uuid);
        assertEquals(0, area.version);
        assertEquals(0, area.baselineVersion);
        assertNull(area.baselineSnapshot);
        assertFalse(area.synced);
        assertEquals(0L, area.lastUpdated);
        assertEquals(EnumSet.allOf(AreaFieldGroup.class), area.dirtyGroups);
    }

    @Test void duplicateKeepsDirectionButGetsIndependentIdentity() {
        NArea source = new NArea("source");
        source.id = 3;
        source.space = new NArea.Space();
        source.uuid = "source-uuid";
        source.version = 7;
        source.pileFillDirection = PileFillDirection.RIGHT_TO_LEFT;

        NArea copy = AreaCreation.duplicate(source, 4, "source (copy)");

        assertEquals(4, copy.id);
        assertEquals("source (copy)", copy.name);
        assertEquals(PileFillDirection.RIGHT_TO_LEFT, copy.pileFillDirection);
        assertNotEquals(source.uuid, copy.uuid);
        assertEquals(0, copy.version);
        assertEquals(EnumSet.allOf(AreaFieldGroup.class), copy.dirtyGroups);
    }
}
```

- [ ] **Step 6: Run tests and verify the lifecycle helper is absent**

Run: `ant test`

Expected: test compilation fails because `AreaCreation` does not exist.

- [ ] **Step 7: Implement AreaCreation and use it from both NMapView paths**

`AreaCreation.initializeNew` must clear the dirty set before marking all groups, assign `UUID.randomUUID().toString()`, reset `zoneSync`, `version`, `baselineVersion`, `baselineSnapshot`, `synced`, `lastUpdated`, `lastTouchedBy`, and `lastTouchedAt`, then call `markDirty` for every `AreaFieldGroup`.

`AreaCreation.duplicate` must deep-copy through `new JSONObject(source.toJson().toString())`, set the new id/name, clear `gid`, synchronize grid ids, and call `initializeNew`.

Replace the duplicated reset logic in `NMapView`:

```java
// New area, after geometry/path/color have been assigned:
AreaCreation.initializeNew(newArea);

// Duplicate, after unique id and name are known:
NArea copy = AreaCreation.duplicate(source, newId, newName);
```

Keep the existing `glob.map.areas.put`, `createAreaLabel`, and `NConfig.needAreasUpdate()` calls.

- [ ] **Step 8: Run tests and inspect only the intended lifecycle changes**

Run: `ant test`

Expected: all tests pass; the duplicate test proves direction inheritance and independent sync identity.

- [ ] **Step 9: Commit the model/lifecycle unit**

```bash
git add src/nurgling/areas/PileFillDirection.java src/nurgling/areas/AreaCreation.java \
        test/nurgling/areas/PileFillDirectionTest.java test/nurgling/areas/AreaCreationTest.java
git add -p src/nurgling/areas/NArea.java src/nurgling/NMapView.java
git diff --cached --check
git commit -m "feat: add zone pile fill direction model"
```

---

### Task 2: ROUTING snapshots and server JSON synchronization

**Files:**
- Modify: `src/nurgling/areas/AreaSnapshot.java:24-157`
- Modify: `src/nurgling/db/service/AreaService.java:99-198`
- Create: `test/nurgling/areas/AreaSnapshotPileFillDirectionTest.java`
- Create: `test/nurgling/db/service/AreaServicePileFillDirectionTest.java`

**Interfaces:**
- Produces: `AreaSnapshot.pileFillDirection: String`
- Produces: package-private `AreaService.buildDataJson(NArea): JSONObject`
- Consumes: `NArea.PILE_FILL_DIRECTION_JSON` and `PileFillDirection.fromStored(Object)` from Task 1.

- [ ] **Step 1: Write failing snapshot routing tests**

Test that a direction-only change produces exactly `ROUTING`, and that merged JSON takes the direction from the same side as `in/out/spec`:

```java
@Test void directionDifferenceIsRoutingOnly() {
    NArea before = area(PileFillDirection.LEFT_TO_RIGHT);
    NArea after = area(PileFillDirection.BOTTOM_TO_TOP);
    assertEquals(EnumSet.of(AreaFieldGroup.ROUTING),
            AreaSnapshot.diff(AreaSnapshot.of(before), AreaSnapshot.of(after)));
}

@Test void routingMergeTakesRemoteDirection() {
    AreaSnapshot local = AreaSnapshot.of(area(PileFillDirection.LEFT_TO_RIGHT));
    AreaSnapshot remote = AreaSnapshot.of(area(PileFillDirection.TOP_TO_BOTTOM));
    JSONObject merged = AreaSnapshot.buildMergedJson(
            1, "uuid", local, remote, EnumSet.of(AreaFieldGroup.ROUTING), 2);
    assertEquals("TOP_TO_BOTTOM", merged.getString(NArea.PILE_FILL_DIRECTION_JSON));
}
```

The test helper must create an `NArea` with id, empty `Space`, non-null color, and the requested direction.

- [ ] **Step 2: Run tests and verify snapshots ignore direction**

Run: `ant test`

Expected: the direction-only diff is empty or merged JSON lacks `pile_fill_direction`.

- [ ] **Step 3: Extend AreaSnapshot consistently**

Add `pileFillDirection` to the private constructor and `of(NArea)`. Preserve the existing public `of(String name, ..., String specJson, int version)` signature as a compatibility overload that delegates with `LEFT_TO_RIGHT`; add a second overload accepting `String pileFillDirection` immediately before `int version`. `fromStored` keeps its current signature and reads direction from stored JSON with legacy fallback:

```java
String direction = PileFillDirection.fromStored(
        data.has(NArea.PILE_FILL_DIRECTION_JSON)
                ? data.get(NArea.PILE_FILL_DIRECTION_JSON) : null).name();
```

Include direction in the `ROUTING` comparison and write `routingSrc.pileFillDirection` in `buildMergedJson`.

- [ ] **Step 4: Write failing AreaService payload test**

Extracting payload construction is intentional so the server contract can be unit-tested without a live database:

```java
@Test void serverDataIncludesPileFillDirection() {
    NArea area = new NArea("zone");
    area.id = 1;
    area.space = new NArea.Space();
    area.pileFillDirection = PileFillDirection.RIGHT_TO_LEFT;
    JSONObject data = AreaService.buildDataJson(area);
    assertEquals("RIGHT_TO_LEFT",
            data.getString(NArea.PILE_FILL_DIRECTION_JSON));
}
```

- [ ] **Step 5: Run test and verify server payload omits direction**

Run: `ant test`

Expected: compilation fails because `buildDataJson` does not exist.

- [ ] **Step 6: Extract and use buildDataJson**

Move lines that copy `space`, `in`, `out`, and `spec` into a package-private static helper and add the new key:

```java
static JSONObject buildDataJson(NArea area) {
    JSONObject json = area.toJson();
    JSONObject data = new JSONObject();
    for (String key : new String[]{"space", "in", "out", "spec",
            NArea.PILE_FILL_DIRECTION_JSON}) {
        if (json.has(key)) data.put(key, json.get(key));
    }
    return data;
}
```

`saveArea` must use `buildDataJson(area).toString()` as its `dataStr`. Pull conversion continues through `AreaSnapshot.fromStored`, so no second parser is introduced.

- [ ] **Step 7: Run synchronization tests**

Run: `ant test`

Expected: all tests pass, including direction-only dirty detection, merge selection, and server JSON payload.

- [ ] **Step 8: Commit the server synchronization unit**

```bash
git add src/nurgling/areas/AreaSnapshot.java src/nurgling/db/service/AreaService.java \
        test/nurgling/areas/AreaSnapshotPileFillDirectionTest.java \
        test/nurgling/db/service/AreaServicePileFillDirectionTest.java
git diff --cached --check
git commit -m "feat: sync zone pile fill direction"
```

---

### Task 3: Local Areas.db migration and storage round-trip

**Files:**
- Modify: `src/nurgling/areas/db/AreasDBMigrationManager.java:103-424`
- Modify: `src/nurgling/areas/storage/AreaDBStorage.java:32-1120`
- Create: `test/nurgling/areas/db/AreaDBPileFillDirectionTest.java`

**Interfaces:**
- Consumes: `PileFillDirection.fromStored(Object)` from Task 1.
- Produces: migration version 7 with `areas.pile_fill_direction VARCHAR(32) NOT NULL DEFAULT 'LEFT_TO_RIGHT'`.

- [ ] **Step 1: Write an in-memory SQLite migration/storage test**

Use one SQLite connection with `autoCommit(false)`, run `new AreasDBMigrationManager(connection).runMigrations()`, and provide it through an anonymous `DatabaseConnectionManager` whose `getConnection()` returns that same open connection and whose `isAvailable()` returns `true`.

The test must verify all three persistence states:

```java
@Test void migrationDefaultsOldRowsAndStorageRoundTripsDirection() throws Exception {
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
        conn.setAutoCommit(false);
        new AreasDBMigrationManager(conn).runMigrations();

        assertEquals("LEFT_TO_RIGHT", columnDefault(conn, "pile_fill_direction"));

        AreaDBStorage storage = new AreaDBStorage(managerFor(conn));
        NArea area = validArea(41, PileFillDirection.RIGHT_TO_LEFT);
        storage.saveArea(area);
        assertEquals(PileFillDirection.RIGHT_TO_LEFT,
                storage.loadAllAreas().get(41).pileFillDirection);

        area.pileFillDirection = PileFillDirection.TOP_TO_BOTTOM;
        storage.saveArea(area);
        assertEquals(PileFillDirection.TOP_TO_BOTTOM,
                storage.loadAllAreas().get(41).pileFillDirection);

        storage.deleteArea(41);
        area.pileFillDirection = PileFillDirection.BOTTOM_TO_TOP;
        storage.saveArea(area);
        assertEquals(PileFillDirection.BOTTOM_TO_TOP,
                storage.loadAllAreas().get(41).pileFillDirection);
    }
}
```

`validArea` must set id, empty `Space`, non-null color, empty JSON arrays, and direction. `columnDefault` reads `PRAGMA table_info(areas)` and strips SQLite quote characters before comparing.

- [ ] **Step 2: Run test and verify the column is missing**

Run: `ant test`

Expected: test fails because `pile_fill_direction` is absent.

- [ ] **Step 3: Add idempotent migration 7**

Append migration 7 after migration 6. Probe the column with `SELECT pile_fill_direction FROM areas LIMIT 1`; if absent, run:

```sql
ALTER TABLE areas
ADD COLUMN pile_fill_direction VARCHAR(32) NOT NULL DEFAULT 'LEFT_TO_RIGHT'
```

Then normalize legacy/null values:

```sql
UPDATE areas
SET pile_fill_direction = 'LEFT_TO_RIGHT'
WHERE pile_fill_direction IS NULL OR TRIM(pile_fill_direction) = ''
```

Handle only duplicate-column/already-exists errors as successful idempotence; rethrow other SQL errors.

- [ ] **Step 4: Thread the column through every AreaDBStorage path**

Add the column to `loadAllAreas`, `insertArea`, `updateArea`, `restoreArea`, `loadAreaForComparison`, and `getArea` SQL. Read through the defensive parser:

```java
area.pileFillDirection = PileFillDirection.fromStored(
        rs.getString("pile_fill_direction"));
```

Bind through `area.pileFillDirection.name()`, treating a null in-memory value as `LEFT_TO_RIGHT`. Add direction comparison to `hasAreaChanged`:

```java
if (dbArea.pileFillDirection != newArea.pileFillDirection) return true;
```

Carefully renumber prepared-statement indices in insert/update/restore and test both timestamp branches in the existing code.

- [ ] **Step 5: Run the local DB round-trip test**

Run: `ant test`

Expected: all tests pass; insert, update, delete/restore, and reload each preserve the selected direction.

- [ ] **Step 6: Commit the local database unit**

```bash
git add src/nurgling/areas/db/AreasDBMigrationManager.java \
        src/nurgling/areas/storage/AreaDBStorage.java \
        test/nurgling/areas/db/AreaDBPileFillDirectionTest.java
git diff --cached --check
git commit -m "feat: persist pile fill direction in areas database"
```

---

### Task 4: Deterministic candidate order wired from target zones

**Files:**
- Modify: `src/nurgling/areas/NArea.java:498-778`
- Modify: `src/nurgling/tools/Finder.java:536-642`
- Modify: `src/nurgling/actions/PileMaker.java:27-145`
- Modify: `test/nurgling/tools/FinderCandidateOrderTest.java`
- Modify: `test/nurgling/actions/PileMakerTest.java`

**Interfaces:**
- Produces: `NArea.DirectedAreaBounds extends Pair<Coord2d, Coord2d>` with `direction(): PileFillDirection`.
- Produces: `Finder.getFreePlaces(Pair<Coord2d,Coord2d>, NHitBox, double, PileFillDirection, double): ArrayList<Coord2d>`.
- Produces: package-private `Finder.orderCandidateOffsets(List<Double>, List<Double>, PileFillDirection): List<Coord2d>`.
- Produces: package-private `PileMaker.directionFor(Pair<Coord2d,Coord2d>): PileFillDirection`.

- [ ] **Step 1: Replace the nearest-player regression test with four directional-order tests**

Keep `placementCandidateOffsetsUseTheStockpileFootprintStride`. Replace `placementCandidatesAreOrderedNearestToThePlayer` with tests over `x = [1, 2]`, `y = [10, 20]`:

```java
@Test void leftToRightUsesLegacyColumnOrder() {
    assertEquals(Arrays.asList(c(1,10), c(1,20), c(2,10), c(2,20)),
        Finder.orderCandidateOffsets(xs(), ys(), PileFillDirection.LEFT_TO_RIGHT));
}

@Test void rightToLeftReversesColumnsOnly() {
    assertEquals(Arrays.asList(c(2,10), c(2,20), c(1,10), c(1,20)),
        Finder.orderCandidateOffsets(xs(), ys(), PileFillDirection.RIGHT_TO_LEFT));
}

@Test void topToBottomUsesRows() {
    assertEquals(Arrays.asList(c(1,10), c(2,10), c(1,20), c(2,20)),
        Finder.orderCandidateOffsets(xs(), ys(), PileFillDirection.TOP_TO_BOTTOM));
}

@Test void bottomToTopReversesRowsOnly() {
    assertEquals(Arrays.asList(c(1,20), c(2,20), c(1,10), c(2,10)),
        Finder.orderCandidateOffsets(xs(), ys(), PileFillDirection.BOTTOM_TO_TOP));
}
```

- [ ] **Step 2: Run tests and verify directional ordering is absent**

Run: `ant test`

Expected: test compilation fails because `orderCandidateOffsets` does not exist.

- [ ] **Step 3: Implement the pure ordering helper and directional collection**

`orderCandidateOffsets` must copy/reverse axis lists without mutating caller lists, then choose column-major loops for horizontal directions and row-major loops for vertical directions. `collectFreePlaces` must iterate the resulting coordinate offsets before applying its existing hitbox collision filter.

Add the directional `getFreePlaces` overload. Keep the nearest-order overload for unrelated callers, but `PileMaker` must not call it.

- [ ] **Step 4: Add a tagged zone-bounds carrier without changing generic action APIs**

Add this nested value type to `NArea`:

```java
public static final class DirectedAreaBounds extends Pair<Coord2d, Coord2d> {
    private final NArea owner;
    public DirectedAreaBounds(Coord2d a, Coord2d b, NArea owner) {
        super(a, b);
        this.owner = owner;
    }
    public PileFillDirection direction() {
        return owner == null || owner.pileFillDirection == null
                ? PileFillDirection.LEFT_TO_RIGHT : owner.pileFillDirection;
    }
}
```

Return `DirectedAreaBounds` from `getRCArea`, `getLoadedRCArea`, and `getRCAreaFromStoredData` wherever those methods currently construct a plain `Pair`. This preserves source-zone identity across existing pair-based actions. Rectangles created directly with `new Pair` remain valid and use legacy order.

- [ ] **Step 5: Write failing PileMaker direction-source tests**

Add to `PileMakerTest`:

```java
@Test void zoneBoundsExposeLiveDirection() {
    NArea area = new NArea("zone");
    area.pileFillDirection = PileFillDirection.RIGHT_TO_LEFT;
    Pair<Coord2d, Coord2d> bounds = new NArea.DirectedAreaBounds(
            Coord2d.of(0, 0), Coord2d.of(22, 22), area);
    assertEquals(PileFillDirection.RIGHT_TO_LEFT, PileMaker.directionFor(bounds));
    area.pileFillDirection = PileFillDirection.BOTTOM_TO_TOP;
    assertEquals(PileFillDirection.BOTTOM_TO_TOP, PileMaker.directionFor(bounds));
}

@Test void plainBoundsUseLegacyDirection() {
    assertEquals(PileFillDirection.LEFT_TO_RIGHT, PileMaker.directionFor(
            Pair.of(Coord2d.of(0, 0), Coord2d.of(22, 22))));
}
```

- [ ] **Step 6: Run tests and verify PileMaker does not consume direction**

Run: `ant test`

Expected: compilation fails because `PileMaker.directionFor` does not exist.

- [ ] **Step 7: Remove nearest-player sorting from PileMaker**

Delete the `Gob player`/`nearestTo` candidate-order calculation at current lines 113-114. Add `directionFor` and call the directional Finder overload:

```java
PileFillDirection direction = directionFor(out);
List<Coord2d> candidates = exactPos != null
        ? Collections.singletonList(exactPos)
        : Finder.getFreePlaces(out, hitbox, 0, direction, candidateStride);
```

Do not alter `firstSafeCandidate`, `approachAndPreserveEscape`, `exitStartObstacle`, or the post-placement escape checks.

- [ ] **Step 8: Run candidate and escape regression tests**

Run: `ant test`

Expected: all tests pass, including the four orders, legacy fallback, stride, previous-stockpile exit, and safe-candidate behavior.

- [ ] **Step 9: Commit the placement algorithm unit**

```bash
git add -p src/nurgling/areas/NArea.java src/nurgling/tools/Finder.java \
           src/nurgling/actions/PileMaker.java \
           test/nurgling/tools/FinderCandidateOrderTest.java \
           test/nurgling/actions/PileMakerTest.java
git diff --cached --check
git commit -m "fix: fill stockpile zones in configured order"
```

---

### Task 5: Four-arrow direction selector

**Files:**
- Create: `src/nurgling/widgets/NAreaDirectionMenu.java`
- Modify: `src/nurgling/widgets/NAreasWidget.java:450-680`
- Modify: `src/lang/messages.properties:571-580`
- Modify: `src/lang/messages_ru.properties:565-574`
- Create: `test/nurgling/widgets/NAreaDirectionMenuTest.java`

**Interfaces:**
- Produces: `NAreaDirectionMenu(NArea)` local popup widget.
- Produces: package-private `NAreaDirectionMenu.apply(NArea, PileFillDirection): boolean` for model-level testing.
- Consumes: `NArea.setPileFillDirection` from Task 1.

- [ ] **Step 1: Write a failing selector application test**

```java
package nurgling.widgets;

import nurgling.areas.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NAreaDirectionMenuTest {
    @Test void applyChangesDirectionAndMarksRouting() {
        NArea area = new NArea("zone");
        assertTrue(NAreaDirectionMenu.apply(area, PileFillDirection.TOP_TO_BOTTOM));
        assertEquals(PileFillDirection.TOP_TO_BOTTOM, area.pileFillDirection);
        assertTrue(area.dirtyGroups.contains(AreaFieldGroup.ROUTING));
        assertFalse(NAreaDirectionMenu.apply(area, PileFillDirection.TOP_TO_BOTTOM));
    }
}
```

- [ ] **Step 2: Run test and verify the menu class is absent**

Run: `ant test`

Expected: test compilation fails because `NAreaDirectionMenu` does not exist.

- [ ] **Step 3: Build the compact cross menu**

Implement a local popup with four square direction buttons laid out around a center cell: `↑` at `(1,0)`, `←` at `(0,1)`, `→` at `(2,1)`, `↓` at `(1,2)`. Use a focused `DirectionButton` widget that renders an arrow glyph and distinct selected background; do not add external image assets.

Button mapping is exact:

```java
UP    -> PileFillDirection.BOTTOM_TO_TOP
LEFT  -> PileFillDirection.RIGHT_TO_LEFT
RIGHT -> PileFillDirection.LEFT_TO_RIGHT
DOWN  -> PileFillDirection.TOP_TO_BOTTOM
```

On selection, call `apply`, then call `NConfig.needAreasUpdate()` only if it returned `true`, and destroy the popup. Mouse click outside and Escape destroy the popup without mutation. Mouse and key grabs must be released in `destroy()`.

- [ ] **Step 4: Add the context-menu action beside Duplicate**

Add localization keys:

```properties
# messages.properties
area.menu.fill_direction=Fill Direction

# messages_ru.properties
area.menu.fill_direction=Выбор направления
```

Insert `area.menu.fill_direction` immediately after `area.menu.duplicate` in the non-folder `AreaItem` options. In `nchoose`, open the popup at `ui.mc`:

```java
else if (option.name.equals(get("area.menu.fill_direction"))) {
    NAreaDirectionMenu popup = new NAreaDirectionMenu(area);
    ui.root.add(popup, ui.mc);
}
```

The existing flower menu closes through its normal local-choice path.

- [ ] **Step 5: Run selector and localization tests**

Run: `ant test`

Expected: all tests pass; apply is idempotent and marks only routing on the first change.

- [ ] **Step 6: Commit the selector unit**

```bash
git add src/nurgling/widgets/NAreaDirectionMenu.java \
        test/nurgling/widgets/NAreaDirectionMenuTest.java \
        src/lang/messages.properties src/lang/messages_ru.properties
git add -p src/nurgling/widgets/NAreasWidget.java
git diff --cached --check
git commit -m "feat: add zone fill direction selector"
```

---

### Task 6: Selected-zone ground arrow overlay

**Files:**
- Create: `src/nurgling/overlays/NAreaDirectionArrow.java`
- Modify: `src/nurgling/NMapView.java:809-830`
- Create: `test/nurgling/overlays/NAreaDirectionArrowTest.java`

**Interfaces:**
- Produces: `NAreaDirectionArrow(Sprite.Owner, NArea)`.
- Produces: package-private `shouldDraw(boolean editorOpen, boolean selected, boolean locatable): boolean`.
- Produces: package-private `arrowVertices(PileFillDirection): float[]`.

- [ ] **Step 1: Write failing visibility and orientation tests**

```java
@Test void visibleOnlyForSelectedLoadedZoneInOpenEditor() {
    assertTrue(NAreaDirectionArrow.shouldDraw(true, true, true));
    assertFalse(NAreaDirectionArrow.shouldDraw(false, true, true));
    assertFalse(NAreaDirectionArrow.shouldDraw(true, false, true));
    assertFalse(NAreaDirectionArrow.shouldDraw(true, true, false));
}

@Test void oppositeDirectionsMirrorTheirLongAxis() {
    float[] right = NAreaDirectionArrow.arrowVertices(PileFillDirection.LEFT_TO_RIGHT);
    float[] left = NAreaDirectionArrow.arrowVertices(PileFillDirection.RIGHT_TO_LEFT);
    assertEquals(maxX(right), -minX(left), 0.001f);

    float[] down = NAreaDirectionArrow.arrowVertices(PileFillDirection.TOP_TO_BOTTOM);
    float[] up = NAreaDirectionArrow.arrowVertices(PileFillDirection.BOTTOM_TO_TOP);
    assertEquals(maxY(down), -minY(up), 0.001f);
}
```

Include local `minX/maxX/minY/maxY` helpers that iterate vertex triples.

- [ ] **Step 2: Run tests and verify the overlay class is absent**

Run: `ant test`

Expected: test compilation fails because `NAreaDirectionArrow` does not exist.

- [ ] **Step 3: Implement a broad non-clickable arrow model**

Build a base right-pointing arrow from three triangles in local coordinates: two triangles for a `22 x 10` shaft and one triangle for a `16 x 22` head. Store vertices as `(x, y, z)` with `z = 0.5f`; rotate/mirror them for the four enum values. Use `Model.Mode.TRIANGLES` and the same `Homo3D.vertex` layout pattern as `NZoneBorderOverlay`.

Use a translucent gold state and no click target:

```java
Pipe.Op.compose(
    new BaseColor(new Color(255, 190, 40, 185)),
    Clickable.No,
    Pipe.Op.compose(Rendered.last, States.Depthtest.none, States.maskdepth)
)
```

During `draw`, derive:

- editor open from `gui.areas != null && gui.areas.visible()`;
- selected from `gui.areas.al.sel != null && gui.areas.al.sel.area == area`;
- locatable from `area.getLoadedRCArea(false) != null`.

Draw only when `shouldDraw` returns true. Cache one model per direction and switch by the live `area.pileFillDirection`, so choosing a button changes orientation without recreating the dummy.

- [ ] **Step 4: Attach the arrow to the existing center dummy**

In `NMapView.createAreaLabel`, keep `NAreaLabel` and add the new custom overlay to the same virtual gob:

```java
dummy.addcustomol(new NAreaLabel(dummy, area));
dummy.addcustomol(new NAreaDirectionArrow(dummy, area));
```

No separate lifetime map is needed: `destroyDummys`, selection changes, unloaded-zone sync, and window close already control the center dummy. The arrow's stricter draw predicate prevents `showAllZonesAlways` from showing direction arrows while the editor is closed.

- [ ] **Step 5: Run overlay tests and full test suite**

Run: `ant test`

Expected: all tests pass; visibility predicate and four directional models are deterministic.

- [ ] **Step 6: Commit the overlay unit**

```bash
git add src/nurgling/overlays/NAreaDirectionArrow.java \
        test/nurgling/overlays/NAreaDirectionArrowTest.java
git add -p src/nurgling/NMapView.java
git diff --cached --check
git commit -m "feat: show selected zone fill direction"
```

---

### Task 7: End-to-end regression verification

**Files:**
- Verify: `src/nurgling/actions/PileMaker.java`
- Verify: `src/nurgling/tools/Finder.java`
- Verify: `src/nurgling/areas/NArea.java`
- Verify: `src/nurgling/areas/AreaSnapshot.java`
- Verify: `src/nurgling/areas/storage/AreaDBStorage.java`
- Verify: `src/nurgling/widgets/NAreasWidget.java`
- Verify: `src/nurgling/overlays/NAreaDirectionArrow.java`
- Verify: all new and existing tests under `test/nurgling`

**Interfaces:**
- Consumes every interface produced by Tasks 1-6.
- Produces a verified build artifact `bin/hafen.jar` without changing behavior outside this feature.

- [ ] **Step 1: Scan for incomplete persistence paths and stale nearest ordering**

Run:

```bash
rg -n "pile_fill_direction|pileFillDirection" src/nurgling src/lang test/nurgling
rg -n "Finder.getFreePlaces\(out, hitbox, 0, nearestTo|Coord2d nearestTo" src/nurgling/actions/PileMaker.java
```

Expected: the direction appears in model, snapshot, server payload, local storage, UI, overlay, algorithm, and tests; the second command returns no result.

- [ ] **Step 2: Verify duplicate lifecycle invariants**

Run: `ant test`

Expected: all tests pass, including `AreaCreationTest`, `AreaSnapshotPileFillDirectionTest`, `AreaDBPileFillDirectionTest`, `FinderCandidateOrderTest`, `PileMakerTest`, `NAreaDirectionMenuTest`, and `NAreaDirectionArrowTest`.

- [ ] **Step 3: Build the client jar**

Run: `ant bin`

Expected: build succeeds and refreshes `bin/hafen.jar`.

- [ ] **Step 4: Inspect the final diff for unrelated changes and whitespace errors**

Run:

```bash
git diff --check
git status --short
git diff --stat
```

Expected: no whitespace errors. Pre-existing unrelated dirty files remain present and untouched; feature files contain only the approved design.

- [ ] **Step 5: Perform a manual in-client smoke test**

Use one existing zone and one duplicate:

1. Open zone settings and select the existing zone; verify only it shows a broad arrow.
2. Choose each of `→`, `←`, `↓`, `↑`; verify the arrow updates immediately.
3. Close zone settings; verify the arrow disappears even if “show all zones” is enabled.
4. Reopen the client; verify the selected direction survived local DB load.
5. Create enough stockpiles to observe the first two grid positions; verify they follow the selected scan order without nearest-player placement.
6. Stand inside the previous pile before the next creation; verify the character exits and the next pile uses the earliest valid candidate without an unnecessary gap.
7. Duplicate the zone; verify direction is copied, UUID differs, and the duplicate appears after database/server reload.
8. Change only the duplicate's direction; verify the source zone is unchanged.

- [ ] **Step 6: Commit only any verification fixes**

If verification required code changes, stage only those exact hunks and commit:

```bash
git add -p
git diff --cached --check
git commit -m "test: verify zone pile fill direction"
```

If no fixes were required, do not create an empty commit.
