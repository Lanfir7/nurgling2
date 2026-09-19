# Stoat Click Circle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stoat gets the same ground click disc as squirrels; left click is a normal gob hit; dead stoat keeps the disc; forage markers still ignore stoat.

**Architecture:** `HITBOX_PATHS` is a second list next to `CRITTER_PATHS`. Circles and settings use `hasCircle()` / `circlePaths()`. Forage keeps `isCritter()`. Overlay death-removal is gated with `keepWhenDead()`. Click path stays `GobClick` — no `aggro`.

**Tech Stack:** Java Haven client, JUnit 5 via `ant test`, existing Critter Circles settings, player `changes/*.json`.

## Global Constraints

- Only `gfx/kritter/stoat/stoat`. Winter coat is the same gob; do not add a second path.
- Left click on the disc is a normal gob click. Do not call `NUtils.attack` or send `aggro`.
- Do not add stoat to `isCritter()` / `ForagePickupMarker`.
- Collectible critters still drop the overlay on `dead`/`knock`. Only hitbox animals keep it.
- Default stoat colour/radius: purple `(193,0,255,140)`, radius `10` — same as non-rabbit critters.
- Player copy is bilingual, no class names.
- This repo commits only when Denis asks: skip every Commit step unless he asked in this session.

## File structure

- `src/nurgling/overlays/NCritterCircle.java` — `HITBOX_PATHS`, `hasCircle`, `keepWhenDead`, `circlePaths`, `canAttachCircle`, `appendMissingConfigs`, `buildDefaultConfigs`
- `test/nurgling/overlays/NCritterCircleTest.java`
- `src/nurgling/NGob.java` — attach overlay with `hasCircle` + `canAttachCircle`
- `src/nurgling/widgets/options/NCritterCircleSettings.java` — rows from `circlePaths()`, persist missing conf
- `changes/2026-09-19-stoat-click-circle.json`
- Spec: `docs/plans/2026-09-19-stoat-click-circle-design.md`

---

### Task 1: Hitbox classification

**Files:**
- Modify: `src/nurgling/overlays/NCritterCircle.java`
- Test: `test/nurgling/overlays/NCritterCircleTest.java`

**Interfaces:**
- Consumes: existing `isCritter(String)`, `CRITTER_PATHS`, `buildDefaultConfigs()`
- Produces:
  - `public static final String STOAT_PATH = "gfx/kritter/stoat/stoat"`
  - `public static final List<String> HITBOX_PATHS`
  - `public static boolean hasCircle(String resName)`
  - `public static boolean keepWhenDead(String resName)`
  - `public static List<String> circlePaths()`
  - `public static boolean canAttachCircle(String pose, String resName)`
  - `public static void appendMissingConfigs(java.util.List<Object> settings, List<String> paths)`
  - `buildDefaultConfigs()` includes every `circlePaths()` entry

- [ ] **Step 1: Write the failing tests**

Replace `test/nurgling/overlays/NCritterCircleTest.java` with:

```java
package nurgling.overlays;

import nurgling.conf.NCritterCircleConf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCritterCircleTest {
    private static final String STOAT = "gfx/kritter/stoat/stoat";
    private static final String SQUIRREL = "gfx/kritter/squirrel/squirrel";

    @Test
    void dumbledoreIsRecognizedAsCatchableCritter() {
        assertTrue(NCritterCircle.isCritter("gfx/kritter/dumbledore/dumbledore"));
    }

    @Test
    void woodScorpionIsRecognizedAsCatchableCritter() {
        assertTrue(NCritterCircle.isCritter("gfx/kritter/woodscorpion/woodscorpion"));
    }

    @Test
    void stoatIsHitboxNotForageCritter() {
        assertFalse(NCritterCircle.isCritter(STOAT));
        assertTrue(NCritterCircle.hasCircle(STOAT));
        assertTrue(NCritterCircle.keepWhenDead(STOAT));
    }

    @Test
    void squirrelStaysForageCritterAndDropsCircleWhenDead() {
        assertTrue(NCritterCircle.isCritter(SQUIRREL));
        assertTrue(NCritterCircle.hasCircle(SQUIRREL));
        assertFalse(NCritterCircle.keepWhenDead(SQUIRREL));
    }

    @Test
    void canAttachCircleKeepsDeadStoatAndRejectsDeadSquirrel() {
        assertTrue(NCritterCircle.canAttachCircle(null, STOAT));
        assertTrue(NCritterCircle.canAttachCircle("gfx/kritter/stoat/dead", STOAT));
        assertTrue(NCritterCircle.canAttachCircle(null, SQUIRREL));
        assertFalse(NCritterCircle.canAttachCircle("gfx/kritter/squirrel/dead", SQUIRREL));
        assertFalse(NCritterCircle.canAttachCircle("idle", "gfx/kritter/fox/fox"));
    }

    @Test
    void defaultConfigsAndCirclePathsIncludeStoat() {
        assertTrue(NCritterCircle.circlePaths().contains(STOAT));
        assertTrue(NCritterCircle.circlePaths().contains(SQUIRREL));
        List<String> paths = new ArrayList<String>();
        for (NCritterCircleConf conf : NCritterCircle.buildDefaultConfigs())
            paths.add(conf.path);
        assertTrue(paths.contains(STOAT));
        assertEquals(NCritterCircle.circlePaths().size(), paths.size());
    }

    @Test
    void appendMissingConfigsAddsStoatOnce() {
        ArrayList<Object> settings = new ArrayList<Object>();
        NCritterCircle.appendMissingConfigs(settings, NCritterCircle.circlePaths());
        int first = countPath(settings, STOAT);
        NCritterCircle.appendMissingConfigs(settings, NCritterCircle.circlePaths());
        assertEquals(1, first);
        assertEquals(1, countPath(settings, STOAT));
    }

    private static int countPath(List<Object> settings, String path) {
        int n = 0;
        for (Object item : settings) {
            if (item instanceof NCritterCircleConf && path.equals(((NCritterCircleConf) item).path))
                n++;
        }
        return n;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ant test`

Expected: compile error or FAIL — `hasCircle`, `keepWhenDead`, `circlePaths`, `canAttachCircle`, `appendMissingConfigs` do not exist; `buildDefaultConfigs()` has no stoat.

- [ ] **Step 3: Write minimal implementation**

In `src/nurgling/overlays/NCritterCircle.java`:

1. Add imports `java.util.HashSet` if missing.

2. After `CRITTER_PATHS` / `CRITTER_SET`, add:

```java
    public static final String STOAT_PATH = "gfx/kritter/stoat/stoat";

    /** Small animals that need a click disc but are not forage pickups. */
    public static final List<String> HITBOX_PATHS = List.of(STOAT_PATH);

    private static final Set<String> HITBOX_SET = Set.copyOf(HITBOX_PATHS);
```

3. Replace `isCritter` / `buildDefaultConfigs` block with:

```java
    public static boolean isCritter(String resName) {
        if (resName == null) return false;
        if (CRITTER_SET.contains(resName)) return true;
        return resName.matches(".*/(rabbit|bunny)$");
    }

    public static boolean hasCircle(String resName) {
        if (resName == null) return false;
        return isCritter(resName) || HITBOX_SET.contains(resName);
    }

    public static boolean keepWhenDead(String resName) {
        return resName != null && HITBOX_SET.contains(resName);
    }

    public static List<String> circlePaths() {
        ArrayList<String> paths = new ArrayList<String>(CRITTER_PATHS.size() + HITBOX_PATHS.size());
        paths.addAll(CRITTER_PATHS);
        paths.addAll(HITBOX_PATHS);
        return paths;
    }

    public static boolean canAttachCircle(String pose, String resName) {
        if (!hasCircle(resName))
            return false;
        if (pose == null || keepWhenDead(resName))
            return true;
        return !NParser.checkName(pose, "dead", "knock");
    }

    public static void appendMissingConfigs(java.util.List<Object> settings, List<String> paths) {
        if (settings == null || paths == null)
            return;
        HashSet<String> have = new HashSet<String>();
        for (Object item : settings) {
            if (item instanceof NCritterCircleConf)
                have.add(((NCritterCircleConf) item).path);
        }
        for (String path : paths) {
            if (path != null && !have.contains(path)) {
                Color color = isRabbit(path) ? RABBIT_COLOR : DEFAULT_COLOR;
                settings.add(new NCritterCircleConf(path, true, color, DEFAULT_RADIUS));
                have.add(path);
            }
        }
    }

    public static ArrayList<NCritterCircleConf> buildDefaultConfigs() {
        ArrayList<NCritterCircleConf> list = new ArrayList<>();
        appendMissingConfigs(list, circlePaths());
        return list;
    }
```

Do not change `tick()`, `NGob`, settings, or `ForagePickupMarker` in this task.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ant test`

Expected: PASS, including `NCritterCircleTest`.

- [ ] **Step 5: Commit**

Skip unless Denis asked to commit.

```bash
git add test/nurgling/overlays/NCritterCircleTest.java src/nurgling/overlays/NCritterCircle.java
git commit -m "Add stoat as a click hitbox, not a forage critter."
```

---

### Task 2: Show the circle on living and dead stoat

**Files:**
- Modify: `src/nurgling/overlays/NCritterCircle.java` (`tick`)
- Modify: `src/nurgling/NGob.java` (overlay attach around the `isCritter` delayed task)

**Interfaces:**
- Consumes: `hasCircle(String)`, `keepWhenDead(String)`, `canAttachCircle(String, String)` from Task 1
- Produces: overlay added for stoat; squirrel overlay still removed on death

- [ ] **Step 1: Confirm death helpers already fail the old wiring**

No new test file. Task 1 already locks `canAttachCircle` / `keepWhenDead`. This task only wires those helpers.

- [ ] **Step 2: Change overlay `tick` so hitbox animals keep the disc when dead**

In `src/nurgling/overlays/NCritterCircle.java`, replace the death check in `tick` with:

```java
        String pose = ((Gob) owner).pose();
        if (pose != null && NParser.checkName(pose, DEAD_KNOCKED) && !keepWhenDead(critterPath))
            return true;
```

Leave the enable/disable slot logic unchanged.

- [ ] **Step 3: Attach the overlay for every `hasCircle` gob, including dead stoat**

In `src/nurgling/NGob.java`, replace the delayed overlay block that currently starts with `if (NCritterCircle.isCritter(name))` with:

```java
            if (NCritterCircle.hasCircle(name))
            {
                delayedOverlayTasks.add(new DelayedOverlayTask(
                        gob ->
                        {
                            if (gob.findol(NCritterCircle.class) != null)
                                return false;
                            return NCritterCircle.canAttachCircle(gob.pose(), name);
                        },
                        gob -> gob.addcustomol(new NCritterCircle(gob, NCritterCircle.getColorForCritter(name), NCritterCircle.getRadiusForCritter(name), name))
                ));
            }
```

Leave `observeForageCritter()` on `isCritter(name)` unchanged.

- [ ] **Step 4: Run tests**

Run: `ant test`

Expected: PASS.

- [ ] **Step 5: Commit**

Skip unless Denis asked to commit.

```bash
git add src/nurgling/overlays/NCritterCircle.java src/nurgling/NGob.java
git commit -m "Keep the stoat click circle after death."
```

---

### Task 3: Critter Circles settings row for stoat

**Files:**
- Modify: `src/nurgling/widgets/options/NCritterCircleSettings.java`

**Interfaces:**
- Consumes: `circlePaths()`, `appendMissingConfigs(List<Object>, List<String>)` from Task 1
- Produces: Stoat row in Critter Circles; missing conf appended to global `critterCircleSettings`

- [ ] **Step 1: Point the settings list at `circlePaths()` and persist missing conf**

In `NCritterCircleSettings` constructor, after reading `obj` from `NConfig.getGlobal(NConfig.Key.critterCircleSettings)` and before building `confMap`, if `obj` is an `ArrayList` call:

```java
            NCritterCircle.appendMissingConfigs((ArrayList<Object>) obj, NCritterCircle.circlePaths());
```

Use `@SuppressWarnings("unchecked")` on the constructor or a local block. Then keep filling `confMap` from that list.

Replace:

```java
        for (String path : NCritterCircle.CRITTER_PATHS) {
```

with:

```java
        for (String path : NCritterCircle.circlePaths()) {
```

Keep the `conf == null` fallback (rabbit vs default colour) so a row still appears if the list was not an `ArrayList`.

- [ ] **Step 2: Run tests**

Run: `ant test`

Expected: PASS.

- [ ] **Step 3: Commit**

Skip unless Denis asked to commit.

```bash
git add src/nurgling/widgets/options/NCritterCircleSettings.java
git commit -m "Show stoat in Critter Circles settings."
```

---

### Task 4: Player release note

**Files:**
- Create: `changes/2026-09-19-stoat-click-circle.json`

**Interfaces:**
- Consumes: player-visible behaviour from Tasks 1–3
- Produces: bilingual note, id `2026-09-19-stoat-click-circle`

- [ ] **Step 1: Add the note**

Create `changes/2026-09-19-stoat-click-circle.json`:

```json
{
  "id": "2026-09-19-stoat-click-circle",
  "priority": 5,
  "summary": {
    "ru": "У горностая появился кликабельный круг на земле — проще попасть и подобрать тушку.",
    "en": "Stoats now have a ground click circle, so they are easier to hit and to pick up when dead."
  },
  "detail": {
    "ru": "Как у белок: круг под живым и мёртвым stoat. Клик по кругу — обычный клик по зверю, без автоатаки. Цвет, размер и галочку можно сменить в настройках окружения, Critter Circles, строка Stoat.",
    "en": "Same disc as squirrels, on living and dead stoats. Clicking the circle is a normal click on the animal, not an auto-attack. Colour, size and the checkbox are in Game environment → Critter Circles → Stoat."
  }
}
```

- [ ] **Step 2: Validate notes**

Run: `python tools/release_notes.py --check`

Expected: exit 0.

- [ ] **Step 3: Run tests**

Run: `ant test`

Expected: PASS.

- [ ] **Step 4: Commit**

Skip unless Denis asked to commit.

```bash
git add changes/2026-09-19-stoat-click-circle.json
git commit -m "Note the stoat click circle for players."
```
