package nurgling.overlays;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.bots.MinesweeperSolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static haven.MCache.tilesz;

/**
 * Red crosses on solver DANGER tiles. Green dots only on unmined walls
 * around a tile that was just mined with no dust.
 */
public class MinesweeperDangerMarkers {

    private static final double UPDATE_INTERVAL = 0.3;
    private static final double DUST_WAIT = 0.8;
    private static final int RADIUS = 50;
    private static final int MINE_WATCH_RADIUS = 8;
    private static final int[][] NEIGHBORS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1}
    };

    private enum Mark {
        DANGER, SAFE
    }

    private MinesweeperSolver solver;
    private NGameUI solverGui;
    private final Map<Long, Gob> markers = new HashMap<>();
    private final Map<Long, Mark> kinds = new HashMap<>();
    private final Map<Long, Boolean> prevMineable = new HashMap<>();
    private final Map<Long, Double> pendingBlanks = new HashMap<>();
    private final Set<Long> confirmedBlanks = new HashSet<>();
    private double sinceUpdate = UPDATE_INTERVAL;
    private long lastFingerprint = Long.MIN_VALUE;

    static Set<Coord> greenFromFreshBlanks(Iterable<Coord> blanks, Set<Coord> mineable) {
        Set<Coord> green = new HashSet<>();
        for (Coord blank : blanks) {
            for (int[] d : NEIGHBORS) {
                Coord n = new Coord(blank.x + d[0], blank.y + d[1]);
                if (mineable.contains(n)) {
                    green.add(n);
                }
            }
        }
        return green;
    }

    public void tick(double dt) {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.map == null || gui.map.player() == null) {
            return;
        }
        if (solver == null || solverGui != gui) {
            clear(gui);
            prevMineable.clear();
            pendingBlanks.clear();
            confirmedBlanks.clear();
            solver = new MinesweeperSolver(gui);
            solverGui = gui;
        }
        Coord playerTile = gui.map.player().rc.div(tilesz).floor();
        observeMinedTiles(playerTile, dt);

        sinceUpdate += dt;
        long fingerprint = fingerprint(gui);
        boolean numbersChanged = fingerprint != lastFingerprint;
        if (numbersChanged || sinceUpdate >= UPDATE_INTERVAL) {
            lastFingerprint = fingerprint;
            sinceUpdate = 0;
            if (fingerprint != 0) {
                solver.refresh(playerTile, RADIUS);
                solver.scanMinesweeperNumbers();
                solver.solve();
            }
        }
        sync(gui);
    }

    private void observeMinedTiles(Coord playerTile, double dt) {
        for (int x = playerTile.x - MINE_WATCH_RADIUS; x <= playerTile.x + MINE_WATCH_RADIUS; x++) {
            for (int y = playerTile.y - MINE_WATCH_RADIUS; y <= playerTile.y + MINE_WATCH_RADIUS; y++) {
                Boolean cur = solver.mineableOrUnknown(x, y);
                if (cur == null) {
                    continue;
                }
                long k = key(x, y);
                Boolean prev = prevMineable.put(k, cur);
                if (Boolean.TRUE.equals(prev) && Boolean.FALSE.equals(cur)) {
                    pendingBlanks.put(k, 0.0);
                    confirmedBlanks.remove(k);
                }
            }
        }

        solver.scanMinesweeperNumbers();
        Iterator<Map.Entry<Long, Double>> pending = pendingBlanks.entrySet().iterator();
        while (pending.hasNext()) {
            Map.Entry<Long, Double> e = pending.next();
            Coord tile = new Coord(keyX(e.getKey()), keyY(e.getKey()));
            int number = solver.getNumber(tile);
            if (number > 0) {
                confirmedBlanks.remove(e.getKey());
                pending.remove();
                continue;
            }
            double wait = e.getValue() + dt;
            if (number == 0 || wait >= DUST_WAIT) {
                confirmedBlanks.add(e.getKey());
                pending.remove();
            } else {
                e.setValue(wait);
            }
        }

        int prune = RADIUS * 2;
        confirmedBlanks.removeIf(k ->
                Math.abs(keyX(k) - playerTile.x) > prune || Math.abs(keyY(k) - playerTile.y) > prune);
        prevMineable.entrySet().removeIf(e ->
                Math.abs(keyX(e.getKey()) - playerTile.x) > prune || Math.abs(keyY(e.getKey()) - playerTile.y) > prune);
    }

    private static long fingerprint(NGameUI gui) {
        long hash = 0;
        int count = 0;
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                for (Gob.Overlay ol : gob.ols) {
                    if (ol.spr instanceof NMiningNumber) {
                        NMiningNumber nmn = (NMiningNumber) ol.spr;
                        Coord tile = gob.rc.div(tilesz).floor();
                        hash ^= (((long) tile.x) * 73856093L)
                                ^ (((long) tile.y) * 19349663L)
                                ^ (((long) nmn.val) * 83492791L);
                        count++;
                    }
                }
            }
        }
        if (count == 0) {
            return 0;
        }
        return hash ^ ((long) count << 32);
    }

    private void sync(NGameUI gui) {
        OCache oc = gui.ui.sess.glob.oc;
        Map<Long, Mark> wanted = new HashMap<>();

        Set<Coord> blanks = new HashSet<>();
        Set<Coord> mineable = new HashSet<>();
        for (long k : confirmedBlanks) {
            blanks.add(new Coord(keyX(k), keyY(k)));
            int x = keyX(k);
            int y = keyY(k);
            for (int[] d : NEIGHBORS) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (Boolean.TRUE.equals(solver.mineableOrUnknown(nx, ny))) {
                    mineable.add(new Coord(nx, ny));
                }
            }
        }
        for (Coord tile : greenFromFreshBlanks(blanks, mineable)) {
            wanted.put(key(tile.x, tile.y), Mark.SAFE);
        }
        if (lastFingerprint != 0) {
            for (Coord tile : solver.dangerTiles()) {
                wanted.put(key(tile.x, tile.y), Mark.DANGER);
            }
        }

        for (Map.Entry<Long, Mark> e : wanted.entrySet()) {
            long k = e.getKey();
            Mark kind = e.getValue();
            Gob dummy = markers.get(k);
            if (dummy == null || oc.getgob(dummy.id) == null || kinds.get(k) != kind) {
                if (dummy != null) {
                    removeGob(oc, dummy);
                }
                Coord tile = new Coord(keyX(k), keyY(k));
                Gob created = createMarker(oc, tile, kind);
                markers.put(k, created);
                kinds.put(k, kind);
            }
        }
        Iterator<Map.Entry<Long, Gob>> it = markers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Gob> e = it.next();
            if (!wanted.containsKey(e.getKey())) {
                removeGob(oc, e.getValue());
                kinds.remove(e.getKey());
                it.remove();
            }
        }
    }

    private static Gob createMarker(OCache oc, Coord tile, Mark kind) {
        Coord2d pos = new Coord2d((tile.x + 0.5) * tilesz.x, (tile.y + 0.5) * tilesz.y);
        OCache.Virtual created = oc.new Virtual(pos, 0);
        created.virtual = true;
        Sprite spr = kind == Mark.DANGER
                ? new NMiningDangerOverlay(created)
                : new NMiningSafeOverlay(created);
        created.addol(new Gob.Overlay(created, spr), false);
        oc.add(created);
        return created;
    }

    private void clear(NGameUI gui) {
        if (markers.isEmpty()) {
            return;
        }
        OCache oc = gui.ui.sess.glob.oc;
        for (Gob dummy : markers.values()) {
            removeGob(oc, dummy);
        }
        markers.clear();
        kinds.clear();
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
