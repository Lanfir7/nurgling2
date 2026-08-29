package nurgling.overlays;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.actions.bots.MinesweeperSolver;
import nurgling.conf.NMiningOverlayMemory;

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
    private final Map<Long, Gob> numberMarkers = new HashMap<>();
    private final Map<Long, Boolean> prevMineable = new HashMap<>();
    private final Map<Long, Double> pendingBlanks = new HashMap<>();
    private final Set<Long> confirmedBlanks = new HashSet<>();
    private NMiningOverlayMemory memory;
    private String memUser;
    private String memChr;
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
            restoreNow(gui);
        }
        Coord playerTile = gui.map.player().rc.div(tilesz).floor();
        observeMinedTiles(playerTile, dt);

        sinceUpdate += dt;
        long fingerprint = fingerprint(gui);
        boolean numbersChanged = fingerprint != lastFingerprint;
        if (numbersChanged || sinceUpdate >= UPDATE_INTERVAL) {
            lastFingerprint = fingerprint;
            sinceUpdate = 0;
            persistLiveNumbers(gui);
            persistGreens(gui);
            boolean seeded = applyMemory(gui, playerTile);
            if (fingerprint != 0 || seeded) {
                solver.refresh(playerTile, RADIUS);
                solver.scanMinesweeperNumbers();
                applyMemory(gui, playerTile);
                solver.solve();
                lastFingerprint = fingerprint(gui);
            }
            if (memory != null) {
                memory.maybeFlush(System.currentTimeMillis());
            }
        }
        sync(gui);
    }

    public void restoreNow() {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.map == null || gui.map.player() == null) {
            return;
        }
        restoreNow(gui);
        Coord playerTile = gui.map.player().rc.div(tilesz).floor();
        applyMemory(gui, playerTile);
        solver.refresh(playerTile, RADIUS);
        solver.scanMinesweeperNumbers();
        applyMemory(gui, playerTile);
        solver.solve();
        sync(gui);
        if (memory != null) {
            memory.maybeFlush(System.currentTimeMillis());
        }
    }

    private void restoreNow(NGameUI gui) {
        if (solver == null || solverGui != gui) {
            solver = new MinesweeperSolver(gui);
            solverGui = gui;
        }
        memory = null;
        memUser = null;
        memChr = null;
        resolveMemory(gui);
        persistLiveNumbers(gui);
        persistGreens(gui);
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

    private NMiningOverlayMemory resolveMemory(NGameUI gui) {
        if (gui == null || !(gui.ui instanceof NUI) || gui.getCharInfo() == null) {
            return memory;
        }
        NUI.NSessInfo sess = ((NUI) gui.ui).sessInfo;
        if (sess == null || sess.username == null) {
            return memory;
        }
        String chrid = gui.getCharInfo().chrid;
        if (chrid == null) {
            return memory;
        }
        if (memory == null || !sess.username.equals(memUser) || !chrid.equals(memChr)) {
            memory = NMiningOverlayMemory.get(sess.username, chrid);
            memUser = sess.username;
            memChr = chrid;
        }
        return memory;
    }

    private MCache mapOf(NGameUI gui) {
        return gui.ui.sess.glob.map;
    }

    private void persistLiveNumbers(NGameUI gui) {
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return;
        }
        MCache map = mapOf(gui);
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob.virtual) {
                    continue;
                }
                for (Gob.Overlay ol : gob.ols) {
                    if (ol.spr instanceof NMiningNumber) {
                        NMiningNumber nmn = (NMiningNumber) ol.spr;
                        Coord tile = gob.rc.div(tilesz).floor();
                        NMiningOverlayMemory.TileRef ref = NMiningOverlayMemory.ofWorld(map, tile);
                        if (ref != null) {
                            mem.putNumber(ref, nmn.val);
                        }
                    }
                }
            }
        }
    }

    private void persistGreens(NGameUI gui) {
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return;
        }
        MCache map = mapOf(gui);
        Set<Coord> blanks = new HashSet<>();
        Set<Coord> mineable = new HashSet<>();
        collectBlankNeighbors(blanks, mineable);
        for (Coord tile : greenFromFreshBlanks(blanks, mineable)) {
            NMiningOverlayMemory.TileRef ref = NMiningOverlayMemory.ofWorld(map, tile);
            if (ref != null) {
                mem.putGreen(ref);
            }
        }
    }

    private boolean applyMemory(NGameUI gui, Coord playerTile) {
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return false;
        }
        MCache map = mapOf(gui);
        OCache oc = gui.ui.sess.glob.oc;
        Set<Long> liveNumberTiles = liveNumberTiles(gui);
        Set<Long> wantedNumbers = new HashSet<>();
        boolean seeded = false;
        for (Map.Entry<NMiningOverlayMemory.TileRef, Integer> e : mem.numbers().entrySet()) {
            Coord tile = NMiningOverlayMemory.toWorld(map, e.getKey());
            if (tile == null) {
                continue;
            }
            if (Math.abs(tile.x - playerTile.x) > RADIUS || Math.abs(tile.y - playerTile.y) > RADIUS) {
                continue;
            }
            solver.putState(tile.x, tile.y, MinesweeperSolver.TileState.REVEALED);
            solver.putNumber(tile.x, tile.y, e.getValue());
            seeded = true;
            if (e.getValue() <= 0) {
                continue;
            }
            long k = key(tile.x, tile.y);
            wantedNumbers.add(k);
            if (liveNumberTiles.contains(k)) {
                Gob dummy = numberMarkers.remove(k);
                if (dummy != null) {
                    removeGob(oc, dummy);
                }
                continue;
            }
            Gob dummy = numberMarkers.get(k);
            if (dummy == null || oc.getgob(dummy.id) == null || dummy.findol(NMiningNumber.class) == null) {
                if (dummy != null) {
                    removeGob(oc, dummy);
                }
                numberMarkers.put(k, createNumber(oc, tile, e.getValue()));
            }
        }
        Iterator<Map.Entry<Long, Gob>> it = numberMarkers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Gob> e = it.next();
            if (!wantedNumbers.contains(e.getKey()) || liveNumberTiles.contains(e.getKey())) {
                removeGob(oc, e.getValue());
                it.remove();
            }
        }
        return seeded;
    }

    private Set<Long> liveNumberTiles(NGameUI gui) {
        Set<Long> tiles = new HashSet<>();
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                if (gob.virtual) {
                    continue;
                }
                for (Gob.Overlay ol : gob.ols) {
                    if (ol.spr instanceof NMiningNumber) {
                        Coord tile = gob.rc.div(tilesz).floor();
                        tiles.add(key(tile.x, tile.y));
                    }
                }
            }
        }
        return tiles;
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

    private void collectBlankNeighbors(Set<Coord> blanks, Set<Coord> mineable) {
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
    }

    private void sync(NGameUI gui) {
        OCache oc = gui.ui.sess.glob.oc;
        Map<Long, Mark> wanted = new HashMap<>();

        Set<Coord> blanks = new HashSet<>();
        Set<Coord> mineable = new HashSet<>();
        collectBlankNeighbors(blanks, mineable);
        for (Coord tile : greenFromFreshBlanks(blanks, mineable)) {
            wanted.put(key(tile.x, tile.y), Mark.SAFE);
        }
        rememberedGreens(gui, wanted);
        if (lastFingerprint != 0 || (solver != null && !solver.dangerTiles().isEmpty())) {
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

    private void rememberedGreens(NGameUI gui, Map<Long, Mark> wanted) {
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return;
        }
        MCache map = mapOf(gui);
        Gob player = gui.map.player();
        Coord playerTile = player.rc.div(tilesz).floor();
        for (NMiningOverlayMemory.TileRef ref : mem.greens()) {
            Coord tile = NMiningOverlayMemory.toWorld(map, ref);
            if (tile == null) {
                continue;
            }
            if (Math.abs(tile.x - playerTile.x) > RADIUS || Math.abs(tile.y - playerTile.y) > RADIUS) {
                continue;
            }
            Boolean mineable = solver.mineableOrUnknown(tile.x, tile.y);
            if (Boolean.FALSE.equals(mineable)) {
                mem.removeGreen(ref);
                continue;
            }
            wanted.putIfAbsent(key(tile.x, tile.y), Mark.SAFE);
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

    private static Gob createNumber(OCache oc, Coord tile, int val) {
        Coord2d pos = new Coord2d((tile.x + 0.5) * tilesz.x, (tile.y + 0.5) * tilesz.y);
        OCache.Virtual created = oc.new Virtual(pos, 0);
        created.virtual = true;
        created.addol(new Gob.Overlay(created, new NMiningNumber(created, val)), false);
        oc.add(created);
        return created;
    }

    private void clear(NGameUI gui) {
        OCache oc = gui.ui.sess.glob.oc;
        for (Gob dummy : markers.values()) {
            removeGob(oc, dummy);
        }
        markers.clear();
        kinds.clear();
        for (Gob dummy : numberMarkers.values()) {
            removeGob(oc, dummy);
        }
        numberMarkers.clear();
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
