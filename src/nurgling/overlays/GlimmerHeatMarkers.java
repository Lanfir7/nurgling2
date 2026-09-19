package nurgling.overlays;

import haven.*;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.bots.MinesweeperSolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static haven.MCache.tilesz;

public class GlimmerHeatMarkers {
    private static final double UPDATE_INTERVAL = 0.3;
    private static final double ORE_GOB_HOLD = 1.5;
    private static final int DRAW_RADIUS = 50;
    private static final int MINE_WATCH_RADIUS = 8;

    private GlimmerHeatmap heatmap = new GlimmerHeatmap();
    private MinesweeperSolver solver;
    private NGameUI solverGui;
    private final Map<Long, Boolean> prevMineable = new HashMap<Long, Boolean>();
    private final Map<Long, Boolean> wasOre = new HashMap<Long, Boolean>();
    private final Map<Long, Coord> recentMined = new HashMap<Long, Coord>();
    private final Map<Long, Double> recentAge = new HashMap<Long, Double>();
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
            wasOre.clear();
            recentMined.clear();
            recentAge.clear();
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
        forgetOreGobs(gui, dt);
        if (gui.notices.contains(noticeMark, "glimmers in the vein")) {
            heatmap.onGlimmer(adjacentMineable(playerTile));
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
                if (Boolean.TRUE.equals(cur)) {
                    wasOre.put(k, Boolean.valueOf(GlimmerHeatmap.isOreRock(tileName(x, y))));
                }
                Boolean prev = prevMineable.put(k, cur);
                if (Boolean.TRUE.equals(prev) && Boolean.FALSE.equals(cur)) {
                    Coord tile = Coord.of(x, y);
                    recentMined.put(k, tile);
                    recentAge.put(k, Double.valueOf(0));
                    if (Boolean.TRUE.equals(wasOre.get(k))) {
                        heatmap.ignoreTile(tile);
                    } else {
                        heatmap.onTileCompleted(tile);
                    }
                }
            }
        }
        int prune = DRAW_RADIUS * 2;
        Iterator<Map.Entry<Long, Boolean>> it = prevMineable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Boolean> e = it.next();
            long k = e.getKey();
            if (Math.abs(keyX(k) - playerTile.x) > prune
                    || Math.abs(keyY(k) - playerTile.y) > prune) {
                it.remove();
                wasOre.remove(k);
            }
        }
    }

    private void forgetOreGobs(NGameUI gui, double dt) {
        if (recentMined.isEmpty()) {
            return;
        }
        java.util.Set<Long> oreTiles = oreGobTiles(gui);
        Iterator<Map.Entry<Long, Coord>> it = recentMined.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Coord> e = it.next();
            long k = e.getKey();
            double age = recentAge.getOrDefault(k, Double.valueOf(0)).doubleValue() + dt;
            if (oreTiles.contains(Long.valueOf(k)) || Boolean.TRUE.equals(wasOre.get(k))) {
                heatmap.ignoreTile(e.getValue());
                recentAge.remove(k);
                it.remove();
                continue;
            }
            if (age >= ORE_GOB_HOLD) {
                recentAge.remove(k);
                it.remove();
            } else {
                recentAge.put(k, Double.valueOf(age));
            }
        }
    }

    private java.util.Set<Long> oreGobTiles(NGameUI gui) {
        java.util.Set<Long> tiles = new java.util.HashSet<Long>();
        try {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob == null || gob.virtual || gob.ngob == null || gob.ngob.name == null) {
                    continue;
                }
                if (!GlimmerHeatmap.isOreRock(gob.ngob.name)) {
                    continue;
                }
                Coord tile = gob.rc.div(tilesz).floor();
                tiles.add(Long.valueOf(key(tile.x, tile.y)));
            }
        } catch (Exception ignored) {
        }
        return tiles;
    }

    private String tileName(int x, int y) {
        if (solverGui == null || solverGui.ui == null || solverGui.ui.sess == null) {
            return null;
        }
        try {
            Resource res = solverGui.ui.sess.glob.map.tilesetr(
                    solverGui.ui.sess.glob.map.gettile(new Coord(x, y)));
            return res == null ? null : res.name;
        } catch (Exception e) {
            return null;
        }
    }

    private Set<Coord> adjacentMineable(Coord playerTile) {
        Set<Coord> walls = new HashSet<Coord>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int x = playerTile.x + dx;
                int y = playerTile.y + dy;
                if (Boolean.TRUE.equals(solver.mineableOrUnknown(x, y))) {
                    walls.add(Coord.of(x, y));
                }
            }
        }
        return walls;
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
