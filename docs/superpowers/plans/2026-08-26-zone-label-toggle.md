# Zone Label Live Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Minimap zone-name toggle keeps `NAreaLabel` dummies in sync as grids load, survives closing the area editor, shows hidden zones in gray, and persists across restart.

**Architecture:** Pure `AreaLabelSync` decides CREATE/SKIP/REMOVE and whether labels should be live. `NMapView.syncAreaLabels()` applies that to dummy gobs, throttled from `tick`. Config key `showAllZonesAlways` is the source of truth for the toggle; `NAreaLabel` reads it, not only the minimap field.

**Tech Stack:** Java 8, JUnit 5 (`ant test`), `NConfig`, existing `NAreaLabel` + virtual gobs.

**Spec:** `docs/superpowers/specs/2026-08-26-zone-label-toggle-design.md`

## Global Constraints

- Keep dummy gobs and `NAreaLabel`; do not redraw names some other way.
- Do not create a label when `getRCArea(false)` is null.
- Hidden zones (`area.hide`) stay gray when the toggle is on.
- Default `showAllZonesAlways` is `false`.
- No commits unless the user asks.
- `ant test` is the verification command.

## File map

| File | Role |
|---|---|
| `src/nurgling/areas/AreaLabelSync.java` | Pure CREATE/SKIP/REMOVE + `labelsShouldBeLive` |
| `test/nurgling/areas/AreaLabelSyncTest.java` | Unit tests for those decisions |
| `src/nurgling/NConfig.java` | Key + default |
| `src/nurgling/NMapView.java` | Idempotent `createAreaLabel`, `syncAreaLabels`, tick, destroy resets `gid` |
| `src/nurgling/overlays/NAreaLabel.java` | Toggle from config; `tick` must not drop overlay if GUI is briefly null |
| `src/nurgling/widgets/NMiniMap.java` | Load toggle from config |
| `src/nurgling/widgets/NMiniMapWnd.java` | Persist toggle; sync vs destroy |
| `src/nurgling/widgets/NAreasWidget.java` | `hide()` destroys only if toggle off |
| `src/nurgling/tools/CheckGridsState.java` | Grid change → `syncAreaLabels`, also when toggle is on |

`initDummys()` becomes a wrapper around `syncAreaLabels()` so existing callers (`NGameUI` async load, `NAreasWidget.show`, reload-from-file) stay correct and stop duplicating dummies.

---

### Task 1: Pure sync helper (TDD)

**Files:**
- Create: `src/nurgling/areas/AreaLabelSync.java`
- Create: `test/nurgling/areas/AreaLabelSyncTest.java`

**Produces:**
- `AreaLabelSync.Action { CREATE, SKIP, REMOVE }`
- `boolean labelsShouldBeLive(boolean toggleOn, boolean editorOpen)`
- `boolean toggleOn(Object confVal)` — `Boolean.TRUE.equals(confVal)`
- `Action decide(boolean areaStillExists, boolean locatable, boolean dummyAlive)`

- [ ] **Step 1: Write failing tests**

```java
package nurgling.areas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AreaLabelSyncTest {
    @Test
    void labelsLiveIffToggleOrEditor() {
        assertFalse(AreaLabelSync.labelsShouldBeLive(false, false));
        assertTrue(AreaLabelSync.labelsShouldBeLive(true, false));
        assertTrue(AreaLabelSync.labelsShouldBeLive(false, true));
        assertTrue(AreaLabelSync.labelsShouldBeLive(true, true));
    }

    @Test
    void toggleOnOnlyTrue() {
        assertFalse(AreaLabelSync.toggleOn(null));
        assertFalse(AreaLabelSync.toggleOn(false));
        assertTrue(AreaLabelSync.toggleOn(true));
    }

    @Test
    void decideCreateSkipRemove() {
        assertEquals(AreaLabelSync.Action.CREATE, AreaLabelSync.decide(true, true, false));
        assertEquals(AreaLabelSync.Action.SKIP, AreaLabelSync.decide(true, true, true));
        assertEquals(AreaLabelSync.Action.SKIP, AreaLabelSync.decide(true, false, false));
        assertEquals(AreaLabelSync.Action.REMOVE, AreaLabelSync.decide(false, true, true));
        assertEquals(AreaLabelSync.Action.REMOVE, AreaLabelSync.decide(false, false, true));
    }
}
```

- [ ] **Step 2: Run to verify fail**

```
ant test
```

Expected: compile error or fail on missing `AreaLabelSync`.

- [ ] **Step 3: Implement**

```java
package nurgling.areas;

public final class AreaLabelSync {
    public enum Action { CREATE, SKIP, REMOVE }

    private AreaLabelSync() {}

    public static boolean labelsShouldBeLive(boolean toggleOn, boolean editorOpen) {
        return toggleOn || editorOpen;
    }

    public static boolean toggleOn(Object confVal) {
        return Boolean.TRUE.equals(confVal);
    }

    public static Action decide(boolean areaStillExists, boolean locatable, boolean dummyAlive) {
        if (!areaStillExists)
            return Action.REMOVE;
        if (dummyAlive)
            return Action.SKIP;
        if (locatable)
            return Action.CREATE;
        return Action.SKIP;
    }
}
```

- [ ] **Step 4: `ant test` — `AreaLabelSyncTest` passes**

---

### Task 2: Config key

**Files:**
- Modify: `src/nurgling/NConfig.java`

**Consumes:** none  
**Produces:** `NConfig.Key.showAllZonesAlways`, default `false`

- [ ] Add `showAllZonesAlways` to the `Key` enum (near other overlay toggles, e.g. after `showAnimalIcons`).
- [ ] `conf.put(Key.showAllZonesAlways, false);` in the constructor defaults (one place is enough if this ctor is the only default block; if a second `conf.put` block exists for similar keys, add it there too so load/migrate does not miss it).

No new test beyond Task 1 `toggleOn`; config enum cannot be unit-tested without constructing `NConfig`.

---

### Task 3: Idempotent create + `syncAreaLabels` + tick

**Files:**
- Modify: `src/nurgling/NMapView.java`

**Consumes:** `AreaLabelSync.*`  
**Produces:** `syncAreaLabels()`, `labelsNeeded()`, `initDummys()` → sync

Dummy is alive iff `area.gid != Long.MIN_VALUE` (`NArea.gid` default) **and** `glob.oc.getgob(area.gid) != null` **and** `dummys.containsKey(area.gid)`.

- [ ] `createAreaLabel(Integer id)`: if area is null, return. If dummy already alive, return. Else existing create (`getRCArea(false)`, virtual gob, set `area.gid`, `dummys.put`, `oc.add`).
- [ ] `destroyDummys()`: after removing gobs, for each area with `gid` in the destroyed set, set `area.gid = Long.MIN_VALUE`. Then `dummys.clear()`.
- [ ] `syncAreaLabels()`:
  1. Snapshot `Set<Long> liveGids` from current areas’ `gid` where dummy is alive.
  2. For each area: `locatable = area.getRCArea(false) != null`; `dummyAlive` as above; `decide(true, locatable, dummyAlive)` → CREATE calls `createAreaLabel`.
  3. For each dummy in `dummys` whose id is not in `liveGids` after step 2 (area gone): `oc.remove`, remove from `dummys`.
- [ ] `initDummys()` body: `syncAreaLabels();`
- [ ] `boolean labelsNeeded()`: `AreaLabelSync.labelsShouldBeLive(AreaLabelSync.toggleOn(NConfig.get(Key.showAllZonesAlways)), gui != null && gui.areas != null && gui.areas.visible())`.
- [ ] Field `private long lastAreaLabelSync = 0;` throttle 500 ms.
- [ ] In `tick(double dt)` after the existing `area.tick` loop:

```java
long now = System.currentTimeMillis();
if (now - lastAreaLabelSync >= 500) {
    lastAreaLabelSync = now;
    if (labelsNeeded())
        syncAreaLabels();
    else if (!dummys.isEmpty())
        destroyDummys();
}
```

Commented-out dummy-recreate loop in this `tick` can be deleted; sync replaces it.

- [ ] `reloadAreasFromFileIfChanged` can keep `destroyDummys()` then `initDummys()` (full rebuild after file replace is correct).

---

### Task 4: Overlay + UI wiring

**Files:**
- Modify: `src/nurgling/overlays/NAreaLabel.java`
- Modify: `src/nurgling/widgets/NMiniMap.java`
- Modify: `src/nurgling/widgets/NMiniMapWnd.java`
- Modify: `src/nurgling/widgets/NAreasWidget.java`
- Modify: `src/nurgling/tools/CheckGridsState.java`

**Consumes:** `AreaLabelSync.toggleOn`, `NConfig.Key.showAllZonesAlways`, `syncAreaLabels` / `destroyDummys` / `labelsNeeded`

- [ ] `NAreaLabel.draw`: `showAllZones = AreaLabelSync.toggleOn(NConfig.get(NConfig.Key.showAllZonesAlways));` — drop the mmap / mmapw field dance. Keep editor-open check and gray `area.hide` branch.
- [ ] `NAreaLabel.tick`: if `getGameUI()` is null, `return false` (keep overlay). Keep `return findGob(id) == null` only when GUI exists.
- [ ] `NMiniMap.loadToggleStates`: load `showAllZonesAlways` from config into the field (checkbox still uses the field).
- [ ] `NMiniMapWnd` toggle `changed`:

```java
NConfig.set(NConfig.Key.showAllZonesAlways, a);
NConfig.needUpdate();
if (miniMap instanceof NMiniMap)
    ((NMiniMap) miniMap).showAllZonesAlways = a;
NMapView mapView = (NUtils.getGameUI() != null) ? (NMapView) NUtils.getGameUI().map : null;
if (mapView != null) {
    if (a)
        mapView.syncAreaLabels();
    else if (!mapView.labelsNeeded())
        mapView.destroyDummys();
}
```

Initial checkbox: `showAllZones.a = AreaLabelSync.toggleOn(NConfig.get(NConfig.Key.showAllZonesAlways));` then copy onto `NMiniMap` field. Remove the comment about one-shot `initDummys`.

- [ ] `NAreasWidget.hide()`: `destroyDummys()` only if `!AreaLabelSync.toggleOn(NConfig.get(Key.showAllZonesAlways))`. `show(true)` can keep `initDummys()` (now sync).
- [ ] `CheckGridsState`: on grid change, if `labelsNeeded()` then `syncAreaLabels()` only — do **not** destroy-all-then-init, and do **not** gate on `areas.visible` alone.

---

### Task 5: Verify

- [ ] `ant test` — full suite, 0 failures (includes `AreaLabelSyncTest`).
- [ ] Manual (after client rebuild): toggle on → names for loaded zones; walk into another zone → name appears without re-toggle; close area editor → names stay; toggle off → names gone; hide-zone → gray; restart with toggle on → names come back as grids load.

## Spec coverage

| Spec | Task |
|---|---|
| Live create as grids load | 3 tick + 4 CheckGridsState |
| Survive editor close | 4 NAreasWidget.hide |
| Hidden = gray | 4 NAreaLabel.draw (unchanged branch) |
| Persist | 2 + 4 NMiniMapWnd |
| Idempotent create | 3 createAreaLabel |
| Destroy when toggle off and editor closed | 3 tick else-destroy + 4 toggle off |
| tick must not drop overlay if GUI null | 4 NAreaLabel.tick |
| Tests: decide / live / default | 1 (+ default in 2) |
