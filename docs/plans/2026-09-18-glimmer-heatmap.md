# Glimmer Ore Heatmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After a rock tile becomes floor, show remaining rock in Chebyshev radius 3 as a gold heatmap with digits when the syslog line `Something glimmers in the vein.` fires, and shrink that set when a tile finishes silently.

**Architecture:** `GlimmerHeatmap` is pure tile math (pending FIFO, committed plus/minus samples, derived heat). `GlimmerHeatMarkers` watches nearby mineable→floor transitions, binds `NNoticeLog` glimmer lines, and draws one virtual gob per visible candidate. Overlay follows `MinesweeperDangerMarkers`; mineable-rock uses `MinesweeperSolver.mineableOrUnknown`.

**Tech Stack:** Java 8 Haven client, JUnit 5 via `ant test`, `NConfig` + QoL checkbox, player `changes/*.json`.

## Global Constraints

- Count a sample only when a mineable rock tile becomes cave floor, not on each swing.
- Chebyshev radius 3: `max(|dx|, |dy|) <= 3` (square 7×7, diagonals included).
- Glimmer line: exact English `Something glimmers in the vein.` Match via `NNoticeLog.contains(..., "glimmers in the vein")`.
- Silent completion excludes that square. Exclusion beats heat.
- Floor and already-mined tiles are never painted.
- Gold fill plus digit on remaining rock. Minesweeper numbers stay on floor.
- Overlay lasts until logout / character change (`gui` change). No manual clear button.
- Setting off: no new samples, overlay hidden; keep heatmap data until logout.
- Reuse the minesweeper mineable-rock check (`NAlias("rock", "tiles/cave")` via `MinesweeperSolver.mineableOrUnknown`). Do not invent a second list.
- Do not change mining bots or add minimap dots.
- Player copy is bilingual, no class names or implementation details.
- Commit only the files named in that task; leave unrelated dirty work alone.

## File structure

- `src/nurgling/overlays/GlimmerHeatmap.java` — pending FIFO + committed samples + `visibleHeat`
- `test/nurgling/overlays/GlimmerHeatmapTest.java` — all tracker tests
- `src/nurgling/overlays/GlimmerHeatMarkers.java` — tick, notices, virtual gobs
- `src/nurgling/overlays/NGlimmerHeatOverlay.java` — gold tile + digit sprite
- `src/nurgling/NMapView.java` — construct and tick the overlay
- `src/nurgling/NConfig.java` — `Key.glimmerHeatmap`, default `true`
- `src/nurgling/widgets/options/QoL.java` — checkbox next to mining overlay
- `src/lang/messages.properties` / `src/lang/messages_ru.properties` — `qol.glimmer_heatmap`
- `changes/2026-09-18-ore-glimmer-heatmap.json` — player note

---

### Task 1: Heatmap math

**Files:**
- Create: `src/nurgling/overlays/GlimmerHeatmap.java`
- Test: `test/nurgling/overlays/GlimmerHeatmapTest.java`

**Interfaces:**
- Consumes: `haven.Coord`
- Produces:
  - `public static final int RADIUS = 3`
  - `public static final double GLIMMER_WAIT = 0.8`
  - `public static boolean inRange(Coord center, Coord tile)` — `max(|dx|,|dy|) <= RADIUS`; false if either coord is null
  - `public void onTileCompleted(Coord tile)` — enqueue pending; ignore null
  - `public void onGlimmer()` — oldest pending becomes a positive sample; ignore if queue empty
  - `public void tick(double dt)` — pending older than `GLIMMER_WAIT` become negative samples
  - `public void clear()`
  - `public java.util.Map<Coord, Integer> visibleHeat(Coord playerTile, int drawRadius, java.util.function.Function<Coord, Boolean> stillRock)` — still-rock, not excluded, heat ≥ 1, within Chebyshev `drawRadius` of player. `stillRock` returns `true` remaining rock, `false` floor/mined, `null` skip (unloaded). Null `playerTile` or `stillRock` returns empty map.

- [ ] **Step 1: Write the failing tests**

Create `test/nurgling/overlays/GlimmerHeatmapTest.java`:

```java
package nurgling.overlays;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlimmerHeatmapTest {

    private static Function<Coord, Boolean> rockExcept(final Coord... mined) {
        final Set<Coord> gone = new HashSet<Coord>();
        for (Coord c : mined) {
            gone.add(c);
        }
        return new Function<Coord, Boolean>() {
            public Boolean apply(Coord tile) {
                return Boolean.valueOf(!gone.contains(tile));
            }
        };
    }

    @Test
    void chebyshevThreeIsSevenBySevenNotManhattan() {
        Coord c = Coord.of(10, 10);
        assertTrue(GlimmerHeatmap.inRange(c, Coord.of(13, 10)));
        assertTrue(GlimmerHeatmap.inRange(c, Coord.of(13, 13)));
        assertFalse(GlimmerHeatmap.inRange(c, Coord.of(14, 10)));
        assertFalse(GlimmerHeatmap.inRange(c, Coord.of(10, 14)));
        int n = 0;
        for (int x = 6; x <= 14; x++) {
            for (int y = 6; y <= 14; y++) {
                if (GlimmerHeatmap.inRange(c, Coord.of(x, y))) {
                    n++;
                }
            }
        }
        assertEquals(49, n);
    }

    @Test
    void oneGlimmerPaintsRemainingRockWithHeatOne() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord dug = Coord.of(10, 10);
        map.onTileCompleted(dug);
        map.onGlimmer();
        Map<Coord, Integer> heat = map.visibleHeat(dug, 20, rockExcept(dug));
        assertFalse(heat.containsKey(dug));
        assertEquals(Integer.valueOf(1), heat.get(Coord.of(13, 13)));
        assertEquals(48, heat.size());
        assertFalse(heat.containsKey(Coord.of(14, 10)));
    }

    @Test
    void silentCompletionExcludesSquareEvenIfLaterGlimmerOverlaps() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord silent = Coord.of(10, 10);
        Coord later = Coord.of(12, 10);
        map.onTileCompleted(silent);
        map.tick(GlimmerHeatmap.GLIMMER_WAIT);
        map.onTileCompleted(later);
        map.onGlimmer();
        Map<Coord, Integer> heat = map.visibleHeat(later, 20, rockExcept(silent, later));
        assertFalse(heat.containsKey(Coord.of(11, 10)));
        assertEquals(Integer.valueOf(1), heat.get(Coord.of(15, 10)));
        assertFalse(heat.containsKey(silent));
        assertFalse(heat.containsKey(later));
    }

    @Test
    void minedCentersAreNotPainted() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord dug = Coord.of(5, 5);
        map.onTileCompleted(dug);
        map.onGlimmer();
        Map<Coord, Integer> heat = map.visibleHeat(dug, 20, rockExcept(dug));
        assertFalse(heat.containsKey(dug));
    }

    @Test
    void fifoBindsLateGlimmerToOldestPendingTile() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord first = Coord.of(0, 0);
        Coord second = Coord.of(8, 0);
        map.onTileCompleted(first);
        map.onTileCompleted(second);
        map.onGlimmer();
        Map<Coord, Integer> beforeTimeout = map.visibleHeat(first, 20, rockExcept(first, second));
        assertEquals(Integer.valueOf(1), beforeTimeout.get(Coord.of(3, 0)));
        assertFalse(beforeTimeout.containsKey(Coord.of(11, 0)));
        map.tick(GlimmerHeatmap.GLIMMER_WAIT);
        Map<Coord, Integer> afterTimeout = map.visibleHeat(second, 20, rockExcept(first, second));
        assertFalse(afterTimeout.containsKey(Coord.of(11, 0)));
        assertEquals(Integer.valueOf(1), afterTimeout.get(Coord.of(3, 0)));
    }

    @Test
    void glimmerWithNoPendingTileIsIgnored() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        map.onGlimmer();
        assertTrue(map.visibleHeat(Coord.of(0, 0), 20, rockExcept()).isEmpty());
    }

    @Test
    void unloadedTilesAreSkippedNotCrashed() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord dug = Coord.of(0, 0);
        map.onTileCompleted(dug);
        map.onGlimmer();
        Function<Coord, Boolean> hole = new Function<Coord, Boolean>() {
            public Boolean apply(Coord tile) {
                if (tile.equals(Coord.of(1, 0))) {
                    return null;
                }
                return Boolean.valueOf(!tile.equals(dug));
            }
        };
        Map<Coord, Integer> heat = map.visibleHeat(dug, 20, hole);
        assertFalse(heat.containsKey(Coord.of(1, 0)));
        assertEquals(Integer.valueOf(1), heat.get(Coord.of(0, 1)));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `ant test`

Expected: compile error `package nurgling.overlays does not exist` / `cannot find symbol GlimmerHeatmap`, or test class fails to compile.

- [ ] **Step 3: Write minimal implementation**

Create `src/nurgling/overlays/GlimmerHeatmap.java`:

```java
package nurgling.overlays;

import haven.Coord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class GlimmerHeatmap {
    public static final int RADIUS = 3;
    public static final double GLIMMER_WAIT = 0.8;

    private static final class Pending {
        final Coord tile;
        double wait;

        Pending(Coord tile) {
            this.tile = tile;
        }
    }

    private static final class Sample {
        final Coord center;
        final boolean positive;

        Sample(Coord center, boolean positive) {
            this.center = center;
            this.positive = positive;
        }
    }

    private final ArrayDeque<Pending> pending = new ArrayDeque<Pending>();
    private final List<Sample> samples = new ArrayList<Sample>();

    public static boolean inRange(Coord center, Coord tile) {
        if (center == null || tile == null) {
            return false;
        }
        int dx = Math.abs(center.x - tile.x);
        int dy = Math.abs(center.y - tile.y);
        return Math.max(dx, dy) <= RADIUS;
    }

    public void onTileCompleted(Coord tile) {
        if (tile == null) {
            return;
        }
        pending.addLast(new Pending(tile));
    }

    public void onGlimmer() {
        Pending p = pending.pollFirst();
        if (p == null) {
            return;
        }
        samples.add(new Sample(p.tile, true));
    }

    public void tick(double dt) {
        if (pending.isEmpty()) {
            return;
        }
        ArrayList<Pending> timedOut = new ArrayList<Pending>();
        for (Pending p : pending) {
            p.wait += dt;
            if (p.wait >= GLIMMER_WAIT) {
                timedOut.add(p);
            }
        }
        for (Pending p : timedOut) {
            pending.remove(p);
            samples.add(new Sample(p.tile, false));
        }
    }

    public void clear() {
        pending.clear();
        samples.clear();
    }

    public Map<Coord, Integer> visibleHeat(Coord playerTile, int drawRadius,
                                           Function<Coord, Boolean> stillRock) {
        if (playerTile == null || stillRock == null) {
            return Collections.emptyMap();
        }
        Set<Coord> excluded = new HashSet<Coord>();
        Map<Coord, Integer> heat = new HashMap<Coord, Integer>();
        for (Sample sample : samples) {
            for (int x = sample.center.x - RADIUS; x <= sample.center.x + RADIUS; x++) {
                for (int y = sample.center.y - RADIUS; y <= sample.center.y + RADIUS; y++) {
                    Coord tile = Coord.of(x, y);
                    if (Math.max(Math.abs(tile.x - playerTile.x), Math.abs(tile.y - playerTile.y)) > drawRadius) {
                        continue;
                    }
                    Boolean rock = stillRock.apply(tile);
                    if (rock == null || !rock.booleanValue()) {
                        continue;
                    }
                    if (!sample.positive) {
                        excluded.add(tile);
                        heat.remove(tile);
                    } else if (!excluded.contains(tile)) {
                        Integer prev = heat.get(tile);
                        heat.put(tile, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
                    }
                }
            }
        }
        return heat;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `ant test`

Expected: `BUILD SUCCESSFUL` and the new `GlimmerHeatmapTest` methods pass.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/overlays/GlimmerHeatmap.java test/nurgling/overlays/GlimmerHeatmapTest.java
git commit -m "Add glimmer heatmap tile math."
```

---

### Task 2: World overlay

**Files:**
- Create: `src/nurgling/overlays/NGlimmerHeatOverlay.java`
- Create: `src/nurgling/overlays/GlimmerHeatMarkers.java`
- Modify: `src/nurgling/NMapView.java` (field next to `minesweeperDangerMarkers`, tick next to `minesweeperDangerMarkers.tick(dt)`)

**Interfaces:**
- Consumes: `GlimmerHeatmap` from Task 1; `MinesweeperSolver.mineableOrUnknown(int x, int y)`; `gui.notices.contains(long since, String...)`
- Produces: `GlimmerHeatMarkers.tick(double dt)` from `NMapView.tick`; `NConfig.Key.glimmerHeatmap` default `true` (checkbox is Task 3)

Add to `src/nurgling/NConfig.java` enum `Key` immediately after `miningol`: `glimmerHeatmap,`

In the defaults block next to `conf.put(Key.miningol, true);`:

```java
conf.put(Key.glimmerHeatmap, true);
```

- [ ] **Step 1: Sprite**

Create `src/nurgling/overlays/NGlimmerHeatOverlay.java` (gold fill + digit, same vertex layout as `NMiningSafeOverlay`, no `marks/mining/` textures so it cannot be confused with cave-in numbers):

```java
package nurgling.overlays;

import haven.*;
import haven.render.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class NGlimmerHeatOverlay extends Sprite implements RenderTree.Node {
    static final VertexArray.Layout pfmt = new VertexArray.Layout(
            new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 20),
            new VertexArray.Layout.Input(Tex2D.texc, new VectorFormat(2, NumberFormat.FLOAT32), 0, 12, 20)
    );

    public final int val;
    final Model emod;
    ColorTex ct;

    private static Color fillFor(int heat) {
        if (heat <= 1) {
            return new Color(107, 78, 24, 210);
        }
        if (heat == 2) {
            return new Color(160, 120, 32, 230);
        }
        return new Color(240, 195, 90, 245);
    }

    private static TexI makeTex(int heat) {
        int size = UI.scale(64);
        BufferedImage img = TexI.mkbuf(new Coord(size, size));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fillFor(heat));
        int pad = UI.scale(4);
        g.fillRoundRect(pad, pad, size - pad * 2, size - pad * 2, UI.scale(10), UI.scale(10));
        g.setColor(new Color(26, 18, 8, 255));
        g.setFont(new Font("SansSerif", Font.BOLD, UI.scale(28)));
        String s = heat > 9 ? "9" : String.valueOf(heat);
        int w = g.getFontMetrics().stringWidth(s);
        int h = g.getFontMetrics().getAscent();
        g.drawString(s, (size - w) / 2, (size + h) / 2 - UI.scale(4));
        g.dispose();
        return new TexI(img);
    }

    public NGlimmerHeatOverlay(Owner owner, int val) {
        super(owner, null);
        this.val = Math.max(1, val);
        ct = makeTex(this.val).st();
        float hx = 0.5f * (float) MCache.tilesz.x;
        float hy = 0.5f * (float) MCache.tilesz.y;
        float[] data = {
                hx, hy, 1f, 1, 1,
                -hx, hy, 1f, 1, 0,
                -hx, -hy, 1f, 0, 0,
                hx, -hy, 1f, 0, 1,
        };
        VertexArray va = new VertexArray(pfmt,
                new VertexArray.Buffer(4 * pfmt.inputs[0].stride, DataBuffer.Usage.STATIC,
                        DataBuffer.Filler.of(data)));
        this.emod = new Model(Model.Mode.TRIANGLE_FAN, va, null);
    }

    public void added(RenderTree.Slot slot) {
        Pipe.Op rmat = Pipe.Op.compose(ct, Clickable.No, Rendered.postpfx, States.Depthtest.none);
        slot.add(emod, rmat);
    }

    public boolean tick(double dt) {
        return false;
    }
}
```

- [ ] **Step 2: Markers**

Create `src/nurgling/overlays/GlimmerHeatMarkers.java`:

```java
package nurgling.overlays;

import haven.*;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.bots.MinesweeperSolver;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static haven.MCache.tilesz;

public class GlimmerHeatMarkers {
    private static final double UPDATE_INTERVAL = 0.3;
    private static final int DRAW_RADIUS = 50;
    private static final int MINE_WATCH_RADIUS = 8;

    private GlimmerHeatmap heatmap = new GlimmerHeatmap();
    private MinesweeperSolver solver;
    private NGameUI solverGui;
    private final Map<Long, Boolean> prevMineable = new HashMap<Long, Boolean>();
    private final Map<Long, Gob> markers = new HashMap<Long, Gob>();
    private final Map<Long, Integer> shown = new HashMap<Long, Integer>();
    private long noticeMark;
    private double sinceRebuild;

    private static boolean enabled() {
        Object v = NConfig.get(NConfig.Key.glimmerHeatmap);
        return v == null || Boolean.TRUE.equals(v);
    }

    public void tick(double dt) {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.map == null) {
            return;
        }
        Gob player = gui.map.player();
        if (player == null) {
            return;
        }
        Coord playerTile = player.rc.div(tilesz).floor();
        if (solver == null || solverGui != gui) {
            clear(gui);
            heatmap = new GlimmerHeatmap();
            prevMineable.clear();
            noticeMark = gui.notices.seq();
            solver = new MinesweeperSolver(gui);
            solverGui = gui;
        }
        if (!enabled()) {
            clearGobs(gui);
            noticeMark = gui.notices.seq();
            return;
        }
        observeMinedTiles(playerTile);
        if (gui.notices.contains(noticeMark, "glimmers in the vein")) {
            heatmap.onGlimmer();
        }
        noticeMark = gui.notices.seq();
        heatmap.tick(dt);
        sinceRebuild += dt;
        if (sinceRebuild >= UPDATE_INTERVAL) {
            sinceRebuild = 0;
            rebuild(gui, playerTile);
        }
    }

    private void observeMinedTiles(Coord playerTile) {
        for (int x = playerTile.x - MINE_WATCH_RADIUS; x <= playerTile.x + MINE_WATCH_RADIUS; x++) {
            for (int y = playerTile.y - MINE_WATCH_RADIUS; y <= playerTile.y + MINE_WATCH_RADIUS; y++) {
                Boolean cur = solver.mineableOrUnknown(x, y);
                if (cur == null) {
                    continue;
                }
                long k = key(x, y);
                Boolean prev = prevMineable.put(k, cur);
                if (Boolean.TRUE.equals(prev) && Boolean.FALSE.equals(cur)) {
                    heatmap.onTileCompleted(Coord.of(x, y));
                }
            }
        }
        int prune = DRAW_RADIUS * 2;
        Iterator<Map.Entry<Long, Boolean>> it = prevMineable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Boolean> e = it.next();
            if (Math.abs(keyX(e.getKey()) - playerTile.x) > prune
                    || Math.abs(keyY(e.getKey()) - playerTile.y) > prune) {
                it.remove();
            }
        }
    }

    private void rebuild(NGameUI gui, final Coord playerTile) {
        Map<Coord, Integer> heat = heatmap.visibleHeat(playerTile, DRAW_RADIUS,
                new java.util.function.Function<Coord, Boolean>() {
                    public Boolean apply(Coord tile) {
                        return solver.mineableOrUnknown(tile.x, tile.y);
                    }
                });
        OCache oc = gui.ui.sess.glob.oc;
        java.util.HashSet<Long> want = new java.util.HashSet<Long>();
        for (Map.Entry<Coord, Integer> e : heat.entrySet()) {
            Coord tile = e.getKey();
            int val = e.getValue().intValue();
            long k = key(tile.x, tile.y);
            want.add(k);
            Integer had = shown.get(k);
            if (had != null && had.intValue() == val && markers.containsKey(k)) {
                continue;
            }
            removeGob(oc, markers.remove(k));
            Gob gob = createMarker(oc, tile, val);
            markers.put(k, gob);
            shown.put(k, Integer.valueOf(val));
        }
        Iterator<Map.Entry<Long, Gob>> mit = markers.entrySet().iterator();
        while (mit.hasNext()) {
            Map.Entry<Long, Gob> e = mit.next();
            if (!want.contains(e.getKey())) {
                removeGob(oc, e.getValue());
                shown.remove(e.getKey());
                mit.remove();
            }
        }
    }

    private static Gob createMarker(OCache oc, Coord tile, int val) {
        Coord2d pos = new Coord2d((tile.x + 0.5) * tilesz.x, (tile.y + 0.5) * tilesz.y);
        OCache.Virtual created = oc.new Virtual(pos, 0);
        created.virtual = true;
        created.addol(new Gob.Overlay(created, new NGlimmerHeatOverlay(created, val)), false);
        oc.add(created);
        return created;
    }

    private void clear(NGameUI gui) {
        clearGobs(gui);
        heatmap.clear();
    }

    private void clearGobs(NGameUI gui) {
        if (gui == null || gui.ui == null || gui.ui.sess == null) {
            markers.clear();
            shown.clear();
            return;
        }
        OCache oc = gui.ui.sess.glob.oc;
        for (Gob dummy : markers.values()) {
            removeGob(oc, dummy);
        }
        markers.clear();
        shown.clear();
    }

    private static void removeGob(OCache oc, Gob dummy) {
        if (dummy != null && oc.getgob(dummy.id) != null) {
            oc.remove(dummy);
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyY(long key) {
        return (int) key;
    }
}
```

- [ ] **Step 3: Tick from NMapView**

In `src/nurgling/NMapView.java` next to `private MinesweeperDangerMarkers minesweeperDangerMarkers;`:

```java
    private GlimmerHeatMarkers glimmerHeatMarkers;
```

In `tick`, immediately after `minesweeperDangerMarkers.tick(dt);`:

```java
        if (glimmerHeatMarkers == null) {
            glimmerHeatMarkers = new GlimmerHeatMarkers();
        }
        glimmerHeatMarkers.tick(dt);
```

Add the import if the overlays import is not already star/package-covered:

```java
import nurgling.overlays.GlimmerHeatMarkers;
```

(`NMapView` already uses `nurgling.overlays.MinesweeperDangerMarkers` — place the new import beside it.)

- [ ] **Step 4: Compile**

Run: `ant test`

Expected: `BUILD SUCCESSFUL`. No overlay widget test. `GlimmerHeatmapTest` still passes.

- [ ] **Step 5: Commit**

```bash
git add src/nurgling/overlays/NGlimmerHeatOverlay.java src/nurgling/overlays/GlimmerHeatMarkers.java src/nurgling/NMapView.java src/nurgling/NConfig.java
git commit -m "Draw glimmer ore heat on remaining rock."
```

---

### Task 3: Setting toggle

**Files:**
- Modify: `src/nurgling/widgets/options/QoL.java` (checkbox under map overlays, next to mining overlay)
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`

**Interfaces:**
- Consumes: `NConfig.Key.glimmerHeatmap` from Task 2
- Produces: QoL checkbox `qol.glimmer_heatmap`; load/save like `miningOL`

- [ ] **Step 1: Strings**

In `src/lang/messages.properties` next to `qol.mining_overlay=Show mining overlay`:

```
qol.glimmer_heatmap=Show ore glimmer heatmap
```

In `src/lang/messages_ru.properties` next to `qol.mining_overlay=Показывать оверлей добычи`:

```
qol.glimmer_heatmap=Показывать теплокарту руды
```

- [ ] **Step 2: Checkbox**

In `QoL.java`:

- Field next to `miningOL`: `private CheckBox glimmerHeat;`
- After `miningOL = leftColumn.add(new CheckBox(L10n.get("qol.mining_overlay")), leftPrev.pos("bl").adds(0, 5));`:

```java
        leftPrev = glimmerHeat = leftColumn.add(new CheckBox(L10n.get("qol.glimmer_heatmap")), leftPrev.pos("bl").adds(0, 5));
```

- In `load()` next to `miningOL.a = getBool(NConfig.Key.miningol);`:

```java
        glimmerHeat.a = getBool(NConfig.Key.glimmerHeatmap);
```

- In `save()` next to `NConfig.set(NConfig.Key.miningol, miningOL.a);`:

```java
        NConfig.set(NConfig.Key.glimmerHeatmap, glimmerHeat.a);
```

- [ ] **Step 3: Compile**

Run: `ant test`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/nurgling/widgets/options/QoL.java src/lang/messages.properties src/lang/messages_ru.properties
git commit -m "Add glimmer heatmap setting next to mining overlay."
```

---

### Task 4: Player note

**Files:**
- Create: `changes/2026-09-18-ore-glimmer-heatmap.json`

**Interfaces:**
- Consumes: `docs/player-release-notes.md` limits (summary ≤180, detail ≤500, bilingual, no class names)
- Produces: note id `2026-09-18-ore-glimmer-heatmap`

- [ ] **Step 1: Write the note**

```json
{
  "id": "2026-09-18-ore-glimmer-heatmap",
  "priority": 7,
  "summary": {
    "ru": "При копке соседняя скала подсвечивается золотом, если жила блеснула — так проще найти руду.",
    "en": "While mining, nearby rock lights up gold when the vein glimmers, so ore is easier to find."
  },
  "detail": {
    "ru": "После выкопанной клетки, если в системном чате есть «Something glimmers in the vein.», оставшаяся скала в квадрате 7×7 красится золотом с цифрой. Несколько копок сужают зону. Тихая копка без сообщения снимает клетки. Выключается в настройках рядом с оверлеем добычи.",
    "en": "After you finish a tile, if System chat says “Something glimmers in the vein.”, remaining rock in a 7×7 square is painted gold with a number. Later digs shrink the zone. A silent tile without that line clears its square. Toggle it next to the mining overlay setting."
  }
}
```

- [ ] **Step 2: Validate**

Run: `python tools/release_notes.py --check`

Expected: exit 0, no error about this id or length.

- [ ] **Step 3: Commit**

```bash
git add changes/2026-09-18-ore-glimmer-heatmap.json
git commit -m "Note the ore glimmer heatmap for players."
```

---

## In-game check after Task 2–3

Not automated. In a cave: mine a wall tile. On glimmer, remaining rock in 7×7 shows gold + `1`. Mine a nearby tile with no message: overlapping gold disappears. Logout clears it. Toggle off hides markers without wiping data for the session.
