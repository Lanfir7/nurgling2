# Vein Miner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Resources bot next to Master Miner that turns on the mine cursor, lets the player finish the first rock tile, then mines every newly visible 8-connected tile of that exact type inside supports or green safe dots, then stops.

**Architecture:** `VeinWorklist` is pure queue math (mined set, visible 8-neighbours, nearest). `VeinSeedCapture` is a one-shot `sel` latch hooked from `NMapView.wdgmsg`. `VeinMiner` activates mine, waits for the player seed, then reuses the existing one-tile mine loop.

**Tech Stack:** Java Haven client, JUnit 5 via `ant test`, `BotRegistry` + bot icon resources + `L10n`, player `changes/*.json`.

## Global Constraints

- One run = one vein; do not re-arm the cursor for a second vein.
- Player mines the seed tile; the bot never mines that first tile.
- Exact tileset name match (`gfx/tiles/rocks/...`), not generic `rock`.
- Queue only **currently visible** same-type tiles; hidden diagonals wait until they open.
- 8-connected: Chebyshev 1 (edges and diagonals).
- Always mine the queued tile closest to the player.
- Bot mines a tile only if it is under `NMiningSupport` or has `NMiningSafeOverlay`.
- Seed is accepted even if unsafe. Bot never mines an unsafe tile.
- Stop on fight, loose rock in range, support hp ≤ 0.25, failed stamina restore, user stop. Empty queue is success.
- Chip nearby bumlings like `MinesweeperMiner`.
- Icon is a copy of Master Miner with unique `@bot.veinminer.title` / `@bot.veinminer.desc` tooltips.
- Player copy is bilingual, no class names. Commit only the files named in that task.

## File structure

- `src/nurgling/actions/bots/VeinWorklist.java` — mined set + visible neighbour queue
- `test/nurgling/actions/bots/VeinWorklistTest.java`
- `src/nurgling/actions/bots/VeinSeedCapture.java` — arm / offer / peek / disarm
- `test/nurgling/actions/bots/VeinSeedCaptureTest.java`
- `src/nurgling/NMapView.java` — forward outbound `sel` to the capture
- `src/nurgling/actions/bots/VeinMiner.java` — Action: cursor, seed wait, mine loop, safety
- `test/nurgling/actions/bots/VeinMinerSafetyTest.java` — support-mask helper
- `src/nurgling/actions/bots/registry/BotRegistry.java` — register after `masterminer`
- `test/nurgling/actions/bots/registry/BotRegistryVeinMinerTest.java`
- `resources/src/nurgling/bots/icons/veinminer/` — copied icon, unique tooltips
- `src/lang/messages.properties` / `src/lang/messages_ru.properties`
- `changes/2026-09-19-vein-miner.json`

---

### Task 1: Vein worklist

**Files:**
- Create: `src/nurgling/actions/bots/VeinWorklist.java`
- Test: `test/nurgling/actions/bots/VeinWorklistTest.java`

**Interfaces:**
- Consumes: `haven.Coord`
- Produces:
  - `public VeinWorklist(String type, Coord seed)` — records seed as already mined; ignore null seed
  - `public void offer(Coord tile, String visibleType, boolean safe)` — enqueue if exact type, safe, Chebyshev-1 from a mined tile, not already mined/queued; `visibleType` null means hidden
  - `public void scanVisible(java.util.function.Function<Coord, String> visibleType, java.util.function.Function<Coord, Boolean> safe)` — offer every 8-neighbour of every mined tile
  - `public Coord takeNearest(Coord playerTile)` — remove and return closest queued tile by `dx*dx+dy*dy`; null if empty or playerTile null
  - `public void markMined(Coord tile)` — seed is already mined; bot tiles call this after a successful dig
  - `public boolean isEmpty()`

- [ ] **Step 1: Write the failing tests**

Create `test/nurgling/actions/bots/VeinWorklistTest.java`:

```java
package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinWorklistTest {

    private static final String ORE = "gfx/tiles/rocks/cassiterite";
    private static final String OTHER = "gfx/tiles/rocks/gneiss";
    private static final Coord SEED = new Coord(5, 5);

    @Test
    void wallFaceEnqueuesOnlyVisibleNeighbours() {
        VeinWorklist list = new VeinWorklist(ORE, SEED);
        Map<Coord, String> visible = new HashMap<Coord, String>();
        visible.put(new Coord(5, 4), ORE);
        visible.put(new Coord(4, 5), ORE);
        visible.put(new Coord(6, 5), ORE);
        Set<Coord> safe = new HashSet<Coord>(visible.keySet());
        list.scanVisible(typeFn(visible), safeFn(safe));

        Set<Coord> got = drain(list, SEED);
        assertEquals(Set.of(new Coord(5, 4), new Coord(4, 5), new Coord(6, 5)), got);
    }

    @Test
    void hiddenDiagonalJoinsOnlyAfterConnectingTileIsMined() {
        VeinWorklist list = new VeinWorklist(ORE, SEED);
        Map<Coord, String> visible = new HashMap<Coord, String>();
        visible.put(new Coord(5, 4), ORE);
        Set<Coord> safe = new HashSet<Coord>();
        safe.add(new Coord(5, 4));
        safe.add(new Coord(4, 4));
        list.scanVisible(typeFn(visible), safeFn(safe));
        assertEquals(new Coord(5, 4), list.takeNearest(SEED));
        list.markMined(new Coord(5, 4));

        visible.put(new Coord(4, 4), ORE);
        list.scanVisible(typeFn(visible), safeFn(safe));
        assertEquals(new Coord(4, 4), list.takeNearest(SEED));
    }

    @Test
    void takeNearestPicksClosestToPlayer() {
        VeinWorklist list = new VeinWorklist(ORE, SEED);
        list.offer(new Coord(8, 5), ORE, true);
        list.offer(new Coord(5, 9), ORE, true);
        assertEquals(new Coord(8, 5), list.takeNearest(new Coord(0, 5)));
    }

    @Test
    void unsafeAndOtherTypeAreRejected() {
        VeinWorklist list = new VeinWorklist(ORE, SEED);
        list.offer(new Coord(5, 4), ORE, false);
        list.offer(new Coord(6, 5), OTHER, true);
        list.offer(new Coord(4, 5), null, true);
        assertTrue(list.isEmpty());
        assertNull(list.takeNearest(SEED));
    }

    private static Function<Coord, String> typeFn(Map<Coord, String> visible) {
        return visible::get;
    }

    private static Function<Coord, Boolean> safeFn(Set<Coord> safe) {
        return safe::contains;
    }

    private static Set<Coord> drain(VeinWorklist list, Coord player) {
        Set<Coord> got = new HashSet<Coord>();
        Coord next;
        while ((next = list.takeNearest(player)) != null) {
            got.add(next);
            list.markMined(next);
        }
        return got;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ant test`

Expected: FAIL compiling or loading `nurgling.actions.bots.VeinWorklistTest` because `VeinWorklist` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/nurgling/actions/bots/VeinWorklist.java`:

```java
package nurgling.actions.bots;

import haven.Coord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class VeinWorklist {
    public static final int[][] NEIGHBORS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1}
    };

    private final String type;
    private final Set<Long> mined = new HashSet<Long>();
    private final Set<Long> queued = new HashSet<Long>();
    private final List<Coord> queue = new ArrayList<Coord>();
    private final List<Coord> minedTiles = new ArrayList<Coord>();

    public VeinWorklist(String type, Coord seed) {
        this.type = type;
        if (seed != null) {
            markMined(seed);
        }
    }

    public void offer(Coord tile, String visibleType, boolean safe) {
        if (tile == null || type == null || !type.equals(visibleType) || !safe) {
            return;
        }
        long k = key(tile);
        if (mined.contains(k) || queued.contains(k) || !adjacentToMined(tile)) {
            return;
        }
        queued.add(k);
        queue.add(tile);
    }

    public void scanVisible(Function<Coord, String> visibleType, Function<Coord, Boolean> safe) {
        if (visibleType == null || safe == null) {
            return;
        }
        List<Coord> snapshot = new ArrayList<Coord>(minedTiles);
        for (Coord m : snapshot) {
            for (int[] d : NEIGHBORS) {
                Coord n = new Coord(m.x + d[0], m.y + d[1]);
                offer(n, visibleType.apply(n), Boolean.TRUE.equals(safe.apply(n)));
            }
        }
    }

    public Coord takeNearest(Coord playerTile) {
        if (playerTile == null || queue.isEmpty()) {
            return null;
        }
        int bestI = 0;
        int bestD = dist2(playerTile, queue.get(0));
        for (int i = 1; i < queue.size(); i++) {
            int d = dist2(playerTile, queue.get(i));
            if (d < bestD) {
                bestD = d;
                bestI = i;
            }
        }
        Coord chosen = queue.remove(bestI);
        queued.remove(key(chosen));
        return chosen;
    }

    public void markMined(Coord tile) {
        if (tile == null) {
            return;
        }
        long k = key(tile);
        mined.add(k);
        queued.remove(k);
        queue.remove(tile);
        if (!minedTiles.contains(tile)) {
            minedTiles.add(tile);
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    private boolean adjacentToMined(Coord tile) {
        for (Coord m : minedTiles) {
            if (Math.max(Math.abs(tile.x - m.x), Math.abs(tile.y - m.y)) == 1) {
                return true;
            }
        }
        return false;
    }

    private static int dist2(Coord a, Coord b) {
        int dx = a.x - b.x;
        int dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    private static long key(Coord c) {
        return ((long) c.x << 32) | (c.y & 0xffffffffL);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `ant test`

Expected: PASS, including `VeinWorklistTest`.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/actions/bots/VeinWorklist.java test/nurgling/actions/bots/VeinWorklistTest.java
git commit -m "Add vein miner worklist for visible 8-connected ore tiles."
```

---

### Task 2: Seed capture from mining `sel`

**Files:**
- Create: `src/nurgling/actions/bots/VeinSeedCapture.java`
- Test: `test/nurgling/actions/bots/VeinSeedCaptureTest.java`
- Modify: `src/nurgling/NMapView.java` (import + field near `pendingPlantingQuality` + `wdgmsg`)

**Interfaces:**
- Consumes: outbound map `sel` (`Coord` start, `Coord` end)
- Produces:
  - `public synchronized void arm()`
  - `public synchronized void disarm()`
  - `public synchronized boolean offer(Coord a, Coord b)` — if armed, store `a` (single-tile click), disarm, return true
  - `public synchronized Coord peek()`
  - `public synchronized boolean isArmed()`
  - `NMapView.veinSeedCapture()` returns the instance used by `VeinMiner`

- [ ] **Step 1: Write the failing tests**

Create `test/nurgling/actions/bots/VeinSeedCaptureTest.java`:

```java
package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinSeedCaptureTest {
    @Test
    void offerIgnoredUntilArmed() {
        VeinSeedCapture cap = new VeinSeedCapture();
        assertFalse(cap.offer(new Coord(1, 2), new Coord(1, 2)));
        assertNull(cap.peek());
    }

    @Test
    void firstArmedSelBecomesSeedAndDisarms() {
        VeinSeedCapture cap = new VeinSeedCapture();
        cap.arm();
        assertTrue(cap.isArmed());
        assertTrue(cap.offer(new Coord(3, 4), new Coord(3, 4)));
        assertEquals(new Coord(3, 4), cap.peek());
        assertFalse(cap.isArmed());
        assertFalse(cap.offer(new Coord(9, 9), new Coord(9, 9)));
        assertEquals(new Coord(3, 4), cap.peek());
    }

    @Test
    void disarmDropsWaitWithoutKeepingLaterSel() {
        VeinSeedCapture cap = new VeinSeedCapture();
        cap.arm();
        cap.disarm();
        assertFalse(cap.offer(new Coord(0, 0), new Coord(0, 0)));
        assertNull(cap.peek());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ant test`

Expected: FAIL because `VeinSeedCapture` does not exist.

- [ ] **Step 3: Write capture + map hook**

Create `src/nurgling/actions/bots/VeinSeedCapture.java`:

```java
package nurgling.actions.bots;

import haven.Coord;

public class VeinSeedCapture {
    private Coord seed;
    private boolean armed;

    public synchronized void arm() {
        seed = null;
        armed = true;
    }

    public synchronized void disarm() {
        armed = false;
    }

    public synchronized boolean offer(Coord a, Coord b) {
        if (!armed || a == null) {
            return false;
        }
        seed = a;
        armed = false;
        return true;
    }

    public synchronized Coord peek() {
        return seed;
    }

    public synchronized boolean isArmed() {
        return armed;
    }
}
```

In `src/nurgling/NMapView.java`:

1. Add import: `import nurgling.actions.bots.VeinSeedCapture;`
2. Next to `private int pendingPlantingQuality = -1;` add:

```java
    private final VeinSeedCapture veinSeedCapture = new VeinSeedCapture();

    public VeinSeedCapture veinSeedCapture() {
        return veinSeedCapture;
    }
```

3. At the top of `wdgmsg`, before the planting `sel` branch, add:

```java
        if ("sel".equals(msg) && args.length >= 2 && args[0] instanceof Coord && args[1] instanceof Coord) {
            veinSeedCapture.offer((Coord) args[0], (Coord) args[1]);
        }
```

Do not skip `super.wdgmsg` — the player’s mine selection must still reach the server.

- [ ] **Step 4: Run tests to verify they pass**

Run: `ant test`

Expected: PASS, including `VeinSeedCaptureTest`. Existing planting-quality `sel` tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/actions/bots/VeinSeedCapture.java test/nurgling/actions/bots/VeinSeedCaptureTest.java src/nurgling/NMapView.java
git commit -m "Latch the next mining selection as the vein miner seed."
```

---

### Task 3: Registry, icon, l10n

**Files:**
- Modify: `src/nurgling/actions/bots/registry/BotRegistry.java` (insert after `masterminer`)
- Create: `resources/src/nurgling/bots/icons/veinminer/` (copy of `masterminer`)
- Modify: `resources/src/nurgling/bots/icons/veinminer/u.res/tooltip/tooltip_0.data`
- Modify: `resources/src/nurgling/bots/icons/veinminer/u.res/tooltip/tooltip_1.data`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`
- Test: `test/nurgling/actions/bots/registry/BotRegistryVeinMinerTest.java`

**Interfaces:**
- Consumes: `VeinMiner.class` (class may be a stub until Task 4; if compile fails, add an empty `VeinMiner implements Action` that `return Results.SUCCESS();` — Task 4 replaces `run`)
- Produces: `BotRegistry.byId("veinminer")` immediately after `masterminer` in `all()`

- [ ] **Step 1: Write the failing tests**

Create `test/nurgling/actions/bots/registry/BotRegistryVeinMinerTest.java`:

```java
package nurgling.actions.bots.registry;

import nurgling.actions.bots.VeinMiner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotRegistryVeinMinerTest {
    @Test
    void veinMinerSitsNextToMasterMiner() {
        BotDescriptor bot = BotRegistry.byId("veinminer");
        assertNotNull(bot);
        assertEquals(BotDescriptor.BotType.RESOURCES, bot.type);
        assertEquals(VeinMiner.class, bot.clazz);
        assertEquals("veinminer", bot.iconPath);
        assertEquals("bot.veinminer.title", bot.titleKey);
        assertEquals("bot.veinminer.desc", bot.descriptionKey);
        assertFalse(bot.allowedAsStepInScenario);
        assertTrue(bot.allowedAsItemInBotMenu);
        List<BotDescriptor> all = BotRegistry.all();
        int master = -1;
        for (int i = 0; i < all.size(); i++) {
            if ("masterminer".equals(all.get(i).id)) master = i;
        }
        assertTrue(master >= 0);
        assertEquals("veinminer", all.get(master + 1).id);
    }

    @Test
    void veinMinerIconsUseOwnTooltipKeys() throws Exception {
        Path icons = Path.of("resources/src/nurgling/bots/icons/veinminer");
        for (String st : new String[] {"u", "h", "d"}) {
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/image/image_0.png")), st + " png");
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/image/image_0.data")), st + " data");
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/meta")), st + " meta");
        }
        String title = Files.readString(icons.resolve("u.res/tooltip/tooltip_0.data"));
        String desc = Files.readString(icons.resolve("u.res/tooltip/tooltip_1.data"));
        assertTrue(title.contains("@bot.veinminer.title"), title);
        assertTrue(desc.contains("@bot.veinminer.desc"), desc);
        assertFalse(title.contains("masterminer"));
        assertFalse(desc.contains("masterminer"));
        assertFalse(title.contains("МастерМайнер"));
    }
}
```

Do not add l10n key assertions here; Task 3 also inserts the keys, and `MasterMinerL10nTest` is not extended.

- [ ] **Step 2: Run tests to verify they fail**

Run: `ant test`

Expected: FAIL — `veinminer` missing from registry and/or icon folder.

- [ ] **Step 3: Register bot, copy icon, add strings**

Copy the icon tree (PowerShell):

```powershell
Copy-Item -Recurse "resources/src/nurgling/bots/icons/masterminer" "resources/src/nurgling/bots/icons/veinminer"
```

Replace `resources/src/nurgling/bots/icons/veinminer/u.res/tooltip/tooltip_0.data` with:

```
#TOOLTIP LAYER FOR RES src/nurgling/wnd/search.res//
#String tooltip
@bot.veinminer.title
```

Replace `resources/src/nurgling/bots/icons/veinminer/u.res/tooltip/tooltip_1.data` with:

```
#TOOLTIP LAYER FOR RES src/nurgling/wnd/search.res//
#String tooltip
@bot.veinminer.desc
```

In `BotRegistry.java`, immediately after the `masterminer` line:

```java
        bots.add(new BotDescriptor("veinminer", BotDescriptor.BotType.RESOURCES, "bot.veinminer.title", "bot.veinminer.desc", false, true, VeinMiner.class, "veinminer", false));
```

In `src/lang/messages.properties` after `bot.masterminer.desc`:

```
bot.veinminer.title=Vein Miner
bot.veinminer.desc=Click a rock tile; the bot mines the rest of that connected vein inside supports or green safe tiles.
```

In `src/lang/messages_ru.properties` after `bot.masterminer.desc`:

```
bot.veinminer.title=Жильный шахтёр
bot.veinminer.desc=Кликни клетку породы — бот докопает связную жилу в подпорках или на зелёных точках.
```

If `VeinMiner` does not exist yet, create this stub (Task 4 replaces `run`):

```java
package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.actions.Action;
import nurgling.actions.Results;

public class VeinMiner implements Action {
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        return Results.SUCCESS();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `ant test`

Expected: PASS, including `BotRegistryVeinMinerTest`.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/actions/bots/registry/BotRegistry.java src/nurgling/actions/bots/VeinMiner.java resources/src/nurgling/bots/icons/veinminer src/lang/messages.properties src/lang/messages_ru.properties test/nurgling/actions/bots/registry/BotRegistryVeinMinerTest.java
git commit -m "Register Vein Miner next to Master Miner with its own icon tooltip."
```

---

### Task 4: VeinMiner action

**Files:**
- Modify: `src/nurgling/actions/bots/VeinMiner.java` (replace stub `run`)
- Test: `test/nurgling/actions/bots/VeinMinerSafetyTest.java`

**Interfaces:**
- Consumes: `VeinWorklist`, `VeinSeedCapture`, `NMapView.veinSeedCapture()`, `NUtils.mine`, `NMiningSupport`, `NMiningSafeOverlay`
- Produces:
  - `public static boolean supportCovers(Coord tile, Coord begin, boolean[][] data)`
  - `public Results run(NGameUI gui)`

Behaviour of `run`:

1. `gui.map` must be `NMapView`. Arm `veinSeedCapture()`, send `act mine`, `GetCurs("mine")`, msg to click a tile.
2. Wait until `peek() != null` or cursor is no longer `mine`. If no seed, disarm and `SUCCESS`.
3. Read tileset name at seed immediately. Wait until that name changes (player finished). Then `new VeinWorklist(type, seed)`.
4. Loop: `scanVisible` with live tileset + `isSafe`; `takeNearest(player tile)`; if null, success. Re-check type and safety; `mineTile`; `markMined`; chip bumlings.
5. `finally` disarm capture.
6. Stop with FAIL on fight, loose rock `< 93.5`, support hp ≤ 0.25 within 150, failed `RestoreResources`.

- [ ] **Step 1: Write the failing safety tests**

Create `test/nurgling/actions/bots/VeinMinerSafetyTest.java`:

```java
package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinMinerSafetyTest {
    @Test
    void supportMaskMatchesBeginOffset() {
        boolean[][] data = new boolean[][] {
                {true, false},
                {false, true}
        };
        Coord begin = new Coord(10, 20);
        assertTrue(VeinMiner.supportCovers(new Coord(10, 20), begin, data));
        assertTrue(VeinMiner.supportCovers(new Coord(11, 21), begin, data));
        assertFalse(VeinMiner.supportCovers(new Coord(11, 20), begin, data));
        assertFalse(VeinMiner.supportCovers(new Coord(9, 20), begin, data));
        assertFalse(VeinMiner.supportCovers(null, begin, data));
        assertFalse(VeinMiner.supportCovers(new Coord(10, 20), begin, null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ant test`

Expected: FAIL because `VeinMiner.supportCovers` does not exist.

- [ ] **Step 3: Implement VeinMiner**

Replace `src/nurgling/actions/bots/VeinMiner.java` with:

```java
package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.overlays.NMiningSafeOverlay;
import nurgling.overlays.NMiningSupport;
import nurgling.tasks.GetCurs;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitChipperState;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.ArrayList;

import static haven.MCache.tilesz;
import static haven.OCache.posres;

public class VeinMiner implements Action {

    private static final NAlias ALL_SUPPORTS = new NAlias(
            "minebeam", "column", "towercap", "ladder", "minesupport", "naturalminesupport"
    );

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null || !(gui.map instanceof NMapView)) {
            return Results.ERROR("No map");
        }
        NMapView map = (NMapView) gui.map;
        VeinSeedCapture cap = map.veinSeedCapture();
        cap.arm();
        try {
            gui.ui.rcvr.rcvmsg(NUtils.getUI().getMenuGridId(), "act", "mine");
            NUtils.addTask(new GetCurs("mine"));
            gui.msg("Vein Miner: click a rock tile");

            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    if (cap.peek() != null) {
                        return true;
                    }
                    String curs = NUtils.getCursorName();
                    return curs != null && NParser.checkName(curs, "arw");
                }
            });

            Coord seed = cap.peek();
            if (seed == null) {
                return Results.SUCCESS();
            }

            String type = tileName(gui, seed);
            if (type == null) {
                return Results.ERROR("Unknown tile");
            }

            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    String now = tileName(gui, seed);
                    return now == null || !type.equals(now);
                }
            });

            VeinWorklist list = new VeinWorklist(type, seed);
            while (true) {
                if (inFight(gui)) {
                    return Results.FAIL();
                }
                list.scanVisible(c -> tileName(gui, c), c -> isSafe(gui, c));
                Coord playerTile = NUtils.player() == null ? null : NUtils.player().rc.div(tilesz).floor();
                Coord next = list.takeNearest(playerTile);
                if (next == null) {
                    gui.msg("Vein Miner: vein finished.");
                    return Results.SUCCESS();
                }
                if (!type.equals(tileName(gui, next)) || !isSafe(gui, next)) {
                    continue;
                }
                Results mined = mineTile(gui, next);
                if (!mined.IsSuccess()) {
                    return mined;
                }
                list.markMined(next);
                handleBumlings(gui);
            }
        } finally {
            cap.disarm();
        }
    }

    public static boolean supportCovers(Coord tile, Coord begin, boolean[][] data) {
        if (tile == null || begin == null || data == null) {
            return false;
        }
        int dx = tile.x - begin.x;
        int dy = tile.y - begin.y;
        return dx >= 0 && dy >= 0 && dx < data.length && data[dx] != null
                && dy < data[dx].length && data[dx][dy];
    }

    static boolean isSafe(NGameUI gui, Coord tile) {
        if (tile == null || gui == null) {
            return false;
        }
        ArrayList<Gob> supports = Finder.findGobs(ALL_SUPPORTS);
        for (Gob g : supports) {
            Gob.Overlay ol = g.findol(NMiningSupport.class);
            if (ol == null || !(ol.spr instanceof NMiningSupport)) {
                continue;
            }
            NMiningSupport nms = (NMiningSupport) ol.spr;
            if (supportCovers(tile, nms.begin, nms.getData())) {
                return true;
            }
        }
        Coord2d world = tileCenter(tile);
        if (gui.ui == null || gui.ui.sess == null) {
            return false;
        }
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob.rc.dist(world) >= tilesz.x) {
                    continue;
                }
                for (Gob.Overlay ol : gob.ols) {
                    if (ol.spr instanceof NMiningSafeOverlay) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static String tileName(NGameUI gui, Coord tile) {
        if (gui == null || gui.ui == null || gui.ui.sess == null || tile == null) {
            return null;
        }
        try {
            Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tile));
            return res == null ? null : res.name;
        } catch (Loading e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Results mineTile(NGameUI gui, Coord tilePos) throws InterruptedException {
        if (inFight(gui)) {
            return Results.FAIL();
        }
        Gob looserock = Finder.findGob(new NAlias("looserock"));
        if (looserock != null && looserock.rc.dist(NUtils.player().rc) < 93.5) {
            return Results.ERROR("Loose rock detected — unsafe to continue");
        }
        if (!checkSupportHealth(tilePos)) {
            return Results.ERROR("Nearby support damaged — unsafe to continue");
        }

        Coord2d worldPos = tileCenter(tilePos);
        NUtils.getDefaultCur();
        PathFinder pf = new PathFinder(NGob.getDummy(worldPos, 0,
                new NHitBox(new Coord2d(-5.5, -5.5), new Coord2d(5.5, 5.5))), true);
        pf.isHardMode = true;
        pf.run(gui);
        if (!new RestoreResources().run(gui).IsSuccess()) {
            return Results.ERROR("Cannot restore resources");
        }

        Resource resBefore = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tilePos));
        while (resBefore != null && resBefore == gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tilePos))) {
            if (inFight(gui)) {
                return Results.FAIL();
            }
            handleBumlings(gui);
            NUtils.mine(worldPos);
            gui.map.wdgmsg("sel", tilePos, tilePos, 0);
            if (NUtils.getStamina() > 0.4) {
                Resource finalResBefore = resBefore;
                Coord finalTilePos = tilePos;
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        Resource current = gui.ui.sess.glob.map.tilesetr(
                                gui.ui.sess.glob.map.gettile(finalTilePos));
                        return current != finalResBefore;
                    }
                });
            }
            gui.map.wdgmsg("click", Coord.z, NUtils.player().rc.floor(posres), 3, 0);
            NUtils.getUI().core.addTask(new GetCurs("arw"));
            resBefore = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tilePos));
            if (!new RestoreResources().run(gui).IsSuccess()) {
                return Results.ERROR("Cannot restore resources");
            }
        }
        return Results.SUCCESS();
    }

    private boolean checkSupportHealth(Coord tilePos) {
        Coord2d worldPos = tileCenter(tilePos);
        ArrayList<Gob> supports = Finder.findGobs(ALL_SUPPORTS);
        for (Gob support : supports) {
            if (support.rc.dist(worldPos) > 150) {
                continue;
            }
            GobHealth health = support.getattr(GobHealth.class);
            if (health != null && health.hp <= 0.25) {
                return false;
            }
        }
        return true;
    }

    private boolean inFight(NGameUI gui) {
        return gui.fv != null && gui.fv.lsrel != null && !gui.fv.lsrel.isEmpty();
    }

    private static Coord2d tileCenter(Coord tile) {
        return new Coord2d(tile.x * tilesz.x + tilesz.x / 2, tile.y * tilesz.y + tilesz.y / 2);
    }

    private void handleBumlings(NGameUI gui) throws InterruptedException {
        Gob bumling = Finder.findGob(new NAlias("bumlings"));
        if (bumling == null || bumling.rc.dist(NUtils.player().rc) > 20) {
            return;
        }
        new PathFinder(bumling).run(gui);
        int attempts = 0;
        while (bumling != null && Finder.findGob(bumling.id) != null && attempts < 10) {
            attempts++;
            if (NUtils.getGameUI().vhand != null) {
                NUtils.drop(NUtils.getGameUI().vhand);
            }
            new SelectFlowerAction("Chip stone", bumling).run(gui);
            WaitChipperState wcs = new WaitChipperState(bumling, true);
            NUtils.getUI().core.addTask(wcs);
            switch (wcs.getState()) {
                case BUMLINGNOTFOUND:
                    bumling = null;
                    break;
                case BUMLINGFORDRINK:
                    new RestoreResources().run(gui);
                    bumling = Finder.findGob(bumling.id);
                    break;
                case DANGER:
                    gui.msg("Warning: Low energy while chipping stones");
                    return;
                default:
                    bumling = Finder.findGob(bumling.id);
                    break;
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `ant test`

Expected: PASS, including `VeinMinerSafetyTest`, `VeinWorklistTest`, `BotRegistryVeinMinerTest`.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/actions/bots/VeinMiner.java test/nurgling/actions/bots/VeinMinerSafetyTest.java
git commit -m "Mine the rest of a visible vein after the player's first tile."
```

---

### Task 5: Player note

**Files:**
- Create: `changes/2026-09-19-vein-miner.json`

**Interfaces:**
- Consumes: `docs/player-release-notes.md`
- Produces: bilingual note, id `2026-09-19-vein-miner`

- [ ] **Step 1: Write the note**

Create `changes/2026-09-19-vein-miner.json`:

```json
{
  "id": "2026-09-19-vein-miner",
  "priority": 7,
  "summary": {
    "ru": "Новый макрос «Жильный шахтёр»: кликни клетку — бот докопает жилу в безопасной зоне.",
    "en": "New Vein Miner macro: click one tile and the bot finishes that vein in the safe zone."
  },
  "detail": {
    "ru": "В ботах добычи, рядом с Мастер-шахтёром. Запуск включает курсор Mine. Выкопайте первую клетку сами — бот запомнит породу и выкопает все открывающиеся соседние клетки того же типа под подпорками или на зелёных точках, затем остановится.",
    "en": "In the mining bots, next to Master Miner. Start it to arm Mine, dig the first tile yourself, and the bot finishes every newly opened neighbour of that rock inside supports or green safe dots, then stops."
  }
}
```

- [ ] **Step 2: Validate notes**

Run: `python tools/release_notes.py --check`

Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add changes/2026-09-19-vein-miner.json
git commit -m "Note the Vein Miner macro for players."
```

---

## Self-review

- Spec coverage: worklist visibility/8-connect/nearest — Task 1. Seed `sel` latch — Task 2. Icon+registry beside Master Miner — Task 3. Cursor, player-first tile, safety OR, mine loop, bumlings, stop conditions — Task 4. Player note — Task 5.
- No second-vein re-arm. Bot does not mine the seed. Hidden diagonals wait for `scanVisible` after `markMined`.
- Types: `VeinWorklist`, `VeinSeedCapture`, `VeinMiner.supportCovers` used consistently.
