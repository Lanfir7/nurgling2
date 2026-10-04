package nurgling.overlays;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.actions.bots.MinesweeperSolver;
import nurgling.conf.NMiningOverlayMemory;
import nurgling.tools.NNoticeLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.function.LongFunction;
import java.util.function.LongToIntFunction;

import static haven.MCache.tilesz;

/**
 * Green dots on unmined walls around a tile that was just mined with no dust.
 * Solver danger tiles are not shown as overlay crosses.
 */
public class MinesweeperDangerMarkers {

    private static final double UPDATE_INTERVAL = 0.3;
    private static final double DUST_WAIT = 0.8;
    private static final double GALLERY_SUPPRESS = 12.0;
    /** Flips just before the gallery message still belong to the opened cave. */
    private static final double GALLERY_FLIP_LOOKBACK = 8.0;
    private static final int RADIUS = 50;
    private static final int MINE_WATCH_RADIUS = 8;
    private static final int GALLERY_WATCH_RADIUS = 24;
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
    private long galleryNoticeMark;
    private double gallerySuppressLeft;
    private final Map<Long, Double> recentFlips = new HashMap<>();
    private final Set<Long> galleryNoGreen = new HashSet<>();
    private NMiningOverlayMemory memory;
    private String memUser;
    private String memChr;
    private final TimedSnapshot<NumberSnapshot> numberSnapshots = newNumberSnapshotCache();
    private Map<Coord, Long> observedGrids = new HashMap<>();
    private final Map<Long, NMiningOverlayMemory.TileRef> sessionBlanks = new HashMap<>();
    private Set<Long> blockedBlanks = Collections.emptySet();

    static final class SnapshotUpdate<T> {
        final T value;
        final boolean refreshed;

        SnapshotUpdate(T value, boolean refreshed) {
            this.value = value;
            this.refreshed = refreshed;
        }
    }

    static final class TimedSnapshot<T> {
        private final double interval;
        private double age;
        private T value;

        TimedSnapshot(double interval) {
            this.interval = interval;
            this.age = interval;
        }

        SnapshotUpdate<T> update(double dt, Supplier<T> capture) {
            age += dt;
            if (value == null || age >= interval) {
                value = capture.get();
                age = 0;
                return new SnapshotUpdate<>(value, true);
            }
            return new SnapshotUpdate<>(value, false);
        }

        void clear() {
            value = null;
            age = interval;
        }
    }

    static <T> TimedSnapshot<T> newNumberSnapshotCache() {
        return new TimedSnapshot<>(UPDATE_INTERVAL);
    }

    private static final class NumberEntry {
        final Coord tile;
        final int value;
        final boolean virtual;

        NumberEntry(Coord tile, int value, boolean virtual) {
            this.tile = tile;
            this.value = value;
            this.virtual = virtual;
        }
    }

    private static final class NumberSnapshot {
        final List<NumberEntry> entries;
        final Set<Long> liveTiles;
        final long fingerprint;
        final Set<Long> blockedTiles;
        final Set<Long> warningTiles;

        NumberSnapshot(List<NumberEntry> entries, Set<Long> liveTiles, long fingerprint,
                       Set<Long> blockedTiles, Set<Long> warningTiles) {
            this.entries = entries;
            this.liveTiles = liveTiles;
            this.fingerprint = fingerprint;
            this.blockedTiles = blockedTiles;
            this.warningTiles = warningTiles;
        }

        static NumberSnapshot capture(NGameUI gui) {
            List<NumberEntry> entries = new ArrayList<>();
            Set<Long> liveTiles = new HashSet<>();
            Set<Long> blockedTiles = new HashSet<>();
            Set<Long> warningTiles = new HashSet<>();
            long hash = 0;
            int count = 0;
            synchronized (gui.ui.sess.glob.oc) {
                for (Gob gob : gui.ui.sess.glob.oc) {
                    for (Gob.Overlay ol : gob.ols) {
                        if (!gob.virtual && ol.spr == null) {
                            Coord tile = gob.rc.div(tilesz).floor();
                            blockedTiles.add(key(tile.x, tile.y));
                        }
                        if (NMiningSafeOverlay.warningSprite(ol.spr)) {
                            Coord tile = gob.rc.div(tilesz).floor();
                            long k = key(tile.x, tile.y);
                            warningTiles.add(k);
                            blockedTiles.add(k);
                        }
                        if (ol.spr instanceof NMiningNumber) {
                            NMiningNumber number = (NMiningNumber) ol.spr;
                            Coord tile = gob.rc.div(tilesz).floor();
                            entries.add(new NumberEntry(tile, number.val, gob.virtual));
                            if (!gob.virtual) {
                                liveTiles.add(key(tile.x, tile.y));
                            }
                            hash ^= (((long) tile.x) * 73856093L)
                                    ^ (((long) tile.y) * 19349663L)
                                    ^ (((long) number.val) * 83492791L);
                            count++;
                        }
                    }
                }
            }
            long fingerprint = count == 0 ? 0 : hash ^ ((long) count << 32);
            return new NumberSnapshot(entries, liveTiles, fingerprint, blockedTiles, warningTiles);
        }

        void applyTo(MinesweeperSolver solver) {
            for (NumberEntry entry : entries) {
                solver.reveal(entry.tile, entry.value);
            }
        }
    }

    static Set<Coord> greenFromFreshBlanks(Iterable<Coord> blanks, Set<Coord> mineable) {
        return greenFromFreshBlanks(blanks, mineable, Collections.<Long>emptySet());
    }

    static Set<Coord> greenFromFreshBlanks(Iterable<Coord> blanks, Set<Coord> mineable,
                                            Set<Long> blocked) {
        Set<Coord> green = new HashSet<>();
        for (Coord blank : blanks) {
            for (int[] d : NEIGHBORS) {
                Coord n = new Coord(blank.x + d[0], blank.y + d[1]);
                if (mineable.contains(n) && !blocked.contains(key(n.x, n.y))) {
                    green.add(n);
                }
            }
        }
        return green;
    }

    static void rememberFlip(Map<Long, Double> flips, long tileKey) {
        flips.put(tileKey, 0.0);
    }

    static void ageFlips(Map<Long, Double> flips, double dt, double keep) {
        Iterator<Map.Entry<Long, Double>> it = flips.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Double> entry = it.next();
            double age = entry.getValue() + Math.max(0, dt);
            if (age > keep) {
                it.remove();
            } else {
                entry.setValue(age);
            }
        }
    }

    static Set<Long> flipsNotOlderThan(Map<Long, Double> flips, double maxAge) {
        Set<Long> young = new HashSet<>();
        for (Map.Entry<Long, Double> entry : flips.entrySet()) {
            if (entry.getValue() <= maxAge) {
                young.add(entry.getKey());
            }
        }
        return young;
    }

    /** Gallery floor plus every neighbor: dust on that cave was never observed. */
    static Set<Long> galleryBlockedTiles(Iterable<Long> floors) {
        Set<Long> blocked = new HashSet<>();
        for (Long floor : floors) {
            if (floor == null) {
                continue;
            }
            int x = keyX(floor);
            int y = keyY(floor);
            blocked.add(floor);
            for (int[] d : NEIGHBORS) {
                blocked.add(key(x + d[0], y + d[1]));
            }
        }
        return blocked;
    }

    static boolean discardGalleryBlankTransitions(NNoticeLog notices, long since,
                                                  Map<Long, Double> pending,
                                                  Set<Long> confirmed) {
        if (notices == null || !notices.contains(since, "cave gallery")) {
            return false;
        }
        pending.clear();
        confirmed.clear();
        return true;
    }

    static boolean recordMinedTileTransition(Boolean previousMineable, Boolean mineable,
                                             boolean suppressFreshBlanks, long tileKey,
                                             Map<Long, Double> pending,
                                             Set<Long> confirmed) {
        if (suppressFreshBlanks || !Boolean.TRUE.equals(previousMineable)
                || !Boolean.FALSE.equals(mineable)) {
            return false;
        }
        pending.put(tileKey, 0.0);
        confirmed.remove(tileKey);
        return true;
    }

    static double nextGallerySuppress(boolean noticedNow, boolean suppressedBurst,
                                      double remaining, double dt, double window) {
        if (noticedNow || suppressedBurst) {
            return window;
        }
        return Math.max(0.0, remaining - dt);
    }

    static Coord snapshotPlayerTile(Supplier<Coord2d> playerPosition) {
        Coord2d position = playerPosition.get();
        return position == null ? null : position.div(tilesz).floor();
    }

    static boolean mapContextChanged(Map<Coord, Long> previous, Map<Coord, Long> current) {
        if (previous.isEmpty()) return false;
        boolean overlap = false;
        for (Map.Entry<Coord, Long> entry : previous.entrySet()) {
            Long id = current.get(entry.getKey());
            if (id != null) {
                if (!id.equals(entry.getValue())) return true;
                overlap = true;
            }
        }
        return !overlap;
    }

    private boolean updateMapContext(NGameUI gui, Coord playerTile) {
        MCache map = mapOf(gui);
        Map<Coord, Long> current = new HashMap<>();
        Coord grid = playerTile.div(MCache.cmaps);
        synchronized (map.grids) {
            for (int x = grid.x - 1; x <= grid.x + 1; x++) {
                for (int y = grid.y - 1; y <= grid.y + 1; y++) {
                    Coord coord = Coord.of(x, y);
                    MCache.Grid loaded = map.grids.get(coord);
                    if (loaded != null) current.put(coord, loaded.id);
                }
            }
        }
        if (!current.containsKey(grid) || mapContextChanged(observedGrids, current)) {
            clear(gui);
            prevMineable.clear();
            pendingBlanks.clear();
            confirmedBlanks.clear();
            recentFlips.clear();
            galleryNoGreen.clear();
            sessionBlanks.clear();
            blockedBlanks = Collections.emptySet();
            numberSnapshots.clear();
            solver = null;
        }
        observedGrids = current;
        return current.containsKey(grid);
    }

    static void revokeNumberedBlanks(Set<Long> confirmed, MinesweeperSolver solver) {
        confirmed.removeIf(k -> solver.getNumber(Coord.of(keyX(k), keyY(k))) > 0);
    }

    static void advanceBlankWaits(Map<Long, Double> pending, Set<Long> confirmed,
                                  Set<Long> fresh, LongToIntFunction numberAt,
                                  LongFunction<Boolean> mineableAt, double dt,
                                  boolean freshNumbers) {
        Iterator<Map.Entry<Long, Double>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Double> entry = iterator.next();
            long k = entry.getKey();
            int number = numberAt.applyAsInt(k);
            if (number > 0) {
                confirmed.remove(k);
                iterator.remove();
                continue;
            }
            // dt describes the interval before this tick, not time since a new transition.
            double age = entry.getValue() + (fresh.contains(k) ? 0 : Math.max(0, dt));
            entry.setValue(age);
            if (freshNumbers && age >= DUST_WAIT && Boolean.FALSE.equals(mineableAt.apply(k))) {
                confirmed.add(k);
                iterator.remove();
            }
        }
    }

    public void tick(double dt) {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.map == null) {
            return;
        }
        Coord playerTile = snapshotPlayerTile(() -> {
            Gob player = gui.map.player();
            return player == null ? null : player.rc;
        });
        if (playerTile == null) {
            return;
        }
        if (!updateMapContext(gui, playerTile)) return;
        if (solver == null || solverGui != gui) {
            clear(gui);
            prevMineable.clear();
            pendingBlanks.clear();
            confirmedBlanks.clear();
            recentFlips.clear();
            galleryNoGreen.clear();
            galleryNoticeMark = gui.notices.seq();
            sessionBlanks.clear();
            blockedBlanks = Collections.emptySet();
            gallerySuppressLeft = 0;
            numberSnapshots.clear();
            solver = new MinesweeperSolver(gui);
            solverGui = gui;
            restoreNow(gui);
        }

        SnapshotUpdate<NumberSnapshot> snapshotUpdate = numberSnapshots.update(
                dt, () -> NumberSnapshot.capture(gui));
        NumberSnapshot snapshot = snapshotUpdate.value;
        if (snapshotUpdate.refreshed) {
            snapshot.applyTo(solver);
            blockedBlanks = snapshot.blockedTiles;
            revokeWarnedMemory(gui, snapshot);
        }
        if (gui.notices.contains(galleryNoticeMark, "cave gallery")) {
            NMiningOverlayMemory mem = resolveMemory(gui);
            if (mem != null) {
                for (NMiningOverlayMemory.TileRef ref : sessionBlanks.values()) mem.removeBlank(ref);
            }
            sessionBlanks.clear();
        }
        boolean galleryBurst = discardGalleryBlankTransitions(
                gui.notices, galleryNoticeMark, pendingBlanks, confirmedBlanks);
        boolean suppressFreshBlanks = galleryBurst || gallerySuppressLeft > 0;
        revokeNumberedBlanks(confirmedBlanks, solver);
        boolean suppressedBurst = observeMinedTiles(playerTile, dt, suppressFreshBlanks,
                snapshotUpdate.refreshed);
        if (galleryBurst) {
            sealGallery(gui, flipsNotOlderThan(recentFlips, GALLERY_FLIP_LOOKBACK));
        } else if (suppressFreshBlanks) {
            sealGallery(gui, flipsNotOlderThan(recentFlips, 0.0));
        }
        gallerySuppressLeft = nextGallerySuppress(
                galleryBurst, suppressedBurst, gallerySuppressLeft, dt, GALLERY_SUPPRESS);
        galleryNoticeMark = gui.notices.seq();

        if (snapshotUpdate.refreshed) {
            persistLiveNumbers(gui, snapshot);
            persistGreens(gui);
            boolean seeded = applyMemory(gui, playerTile, snapshot);
            if (snapshot.fingerprint != 0 || seeded) {
                solver.refresh(playerTile, RADIUS);
                applyMemory(gui, playerTile, snapshot);
                solver.solve();
            }
            if (memory != null) {
                memory.maybeFlush(System.currentTimeMillis());
            }
        }
        sync(gui, playerTile);
    }

    public void restoreNow() {
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null || gui.map == null) {
            return;
        }
        Coord playerTile = snapshotPlayerTile(() -> {
            Gob player = gui.map.player();
            return player == null ? null : player.rc;
        });
        if (playerTile == null) {
            return;
        }
        if (!updateMapContext(gui, playerTile)) return;
        NumberSnapshot snapshot = NumberSnapshot.capture(gui);
        restoreNow(gui, snapshot);
        snapshot.applyTo(solver);
        blockedBlanks = snapshot.blockedTiles;
        revokeWarnedMemory(gui, snapshot);
        applyMemory(gui, playerTile, snapshot);
        solver.refresh(playerTile, RADIUS);
        applyMemory(gui, playerTile, snapshot);
        solver.solve();
        sync(gui, playerTile);
        if (memory != null) {
            memory.maybeFlush(System.currentTimeMillis());
        }
    }

    private void restoreNow(NGameUI gui) {
        restoreNow(gui, NumberSnapshot.capture(gui));
    }

    private void restoreNow(NGameUI gui, NumberSnapshot snapshot) {
        if (solver == null || solverGui != gui) {
            solver = new MinesweeperSolver(gui);
            solverGui = gui;
        }
        memory = null;
        memUser = null;
        memChr = null;
        resolveMemory(gui);
        persistLiveNumbers(gui, snapshot);
        persistGreens(gui);
    }

    private boolean observeMinedTiles(Coord playerTile, double dt, boolean suppressFreshBlanks,
                                     boolean freshNumbers) {
        boolean suppressedBurst = false;
        Set<Long> fresh = new HashSet<>();
        ageFlips(recentFlips, dt, GALLERY_FLIP_LOOKBACK);
        int watch = suppressFreshBlanks ? GALLERY_WATCH_RADIUS : MINE_WATCH_RADIUS;
        for (int x = playerTile.x - watch; x <= playerTile.x + watch; x++) {
            for (int y = playerTile.y - watch; y <= playerTile.y + watch; y++) {
                Boolean cur = solver.mineableOrUnknown(x, y);
                if (cur == null) {
                    continue;
                }
                long k = key(x, y);
                Boolean prev = prevMineable.put(k, cur);
                boolean flipped = Boolean.TRUE.equals(prev) && Boolean.FALSE.equals(cur);
                if (flipped) {
                    rememberFlip(recentFlips, k);
                }
                if (recordMinedTileTransition(prev, cur, suppressFreshBlanks || blockedBlanks.contains(k), k,
                        pendingBlanks, confirmedBlanks)) {
                    fresh.add(k);
                    continue;
                }
                if (suppressFreshBlanks && flipped) {
                    suppressedBurst = true;
                    galleryNoGreen.addAll(galleryBlockedTiles(Collections.singleton(k)));
                }
            }
        }

        advanceBlankWaits(pendingBlanks, confirmedBlanks, fresh,
                k -> solver.getNumber(Coord.of(keyX(k), keyY(k))),
                k -> blockedBlanks.contains(k) ? null
                        : solver.mineableOrUnknown(keyX(k), keyY(k)), dt, freshNumbers);

        int prune = RADIUS * 2;
        confirmedBlanks.removeIf(k ->
                Math.abs(keyX(k) - playerTile.x) > prune || Math.abs(keyY(k) - playerTile.y) > prune);
        prevMineable.entrySet().removeIf(e ->
                Math.abs(keyX(e.getKey()) - playerTile.x) > prune || Math.abs(keyY(e.getKey()) - playerTile.y) > prune);
        pendingBlanks.keySet().removeIf(k ->
                Math.abs(keyX(k) - playerTile.x) > prune || Math.abs(keyY(k) - playerTile.y) > prune);
        sessionBlanks.keySet().removeIf(k -> !confirmedBlanks.contains(k));
        galleryNoGreen.removeIf(k ->
                Math.abs(keyX(k) - playerTile.x) > prune || Math.abs(keyY(k) - playerTile.y) > prune);
        recentFlips.keySet().removeIf(k ->
                Math.abs(keyX(k) - playerTile.x) > prune || Math.abs(keyY(k) - playerTile.y) > prune);
        return suppressedBurst;
    }

    private void sealGallery(NGameUI gui, Set<Long> floors) {
        if (floors.isEmpty()) {
            return;
        }
        galleryNoGreen.addAll(galleryBlockedTiles(floors));
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return;
        }
        MCache map = mapOf(gui);
        for (long k : floors) {
            NMiningOverlayMemory.TileRef ref = NMiningOverlayMemory.ofWorld(map,
                    Coord.of(keyX(k), keyY(k)));
            if (ref != null) {
                mem.removeBlank(ref);
            }
        }
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

    private void persistLiveNumbers(NGameUI gui, NumberSnapshot snapshot) {
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return;
        }
        MCache map = mapOf(gui);
        for (NumberEntry entry : snapshot.entries) {
            if (!persistableDustNumber(entry.virtual, entry.value)) {
                continue;
            }
            NMiningOverlayMemory.TileRef ref = NMiningOverlayMemory.ofWorld(map, entry.tile);
            if (ref != null) {
                mem.putNumber(ref, entry.value);
            }
        }
    }

    private void persistGreens(NGameUI gui) {
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return;
        }
        MCache map = mapOf(gui);
        for (long k : confirmedBlanks) {
            Coord tile = Coord.of(keyX(k), keyY(k));
            if (blockedBlanks.contains(k) || solver.getNumber(tile) > 0
                    || !Boolean.FALSE.equals(solver.mineableOrUnknown(tile.x, tile.y))) continue;
            NMiningOverlayMemory.TileRef ref = NMiningOverlayMemory.ofWorld(map, tile);
            if (ref != null) {
                // Persist the observation, so a late positive number can revoke all its dots.
                mem.putNumber(ref, 0);
                sessionBlanks.put(k, ref);
            }
        }
    }

    private boolean applyMemory(NGameUI gui, Coord playerTile, NumberSnapshot snapshot) {
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return false;
        }
        MCache map = mapOf(gui);
        OCache oc = gui.ui.sess.glob.oc;
        Set<Long> liveNumberTiles = snapshot.liveTiles;
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

    private void collectBlankNeighbors(Set<Coord> blanks, Set<Coord> mineable) {
        for (long k : confirmedBlanks) {
            if (blockedBlanks.contains(k) || solver.getNumber(Coord.of(keyX(k), keyY(k))) > 0
                    || !Boolean.FALSE.equals(solver.mineableOrUnknown(keyX(k), keyY(k)))) continue;
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

    private void sync(NGameUI gui, Coord playerTile) {
        OCache oc = gui.ui.sess.glob.oc;
        Map<Long, Mark> wanted = new HashMap<>();

        Set<Coord> blanks = new HashSet<>();
        Set<Coord> mineable = new HashSet<>();
        collectBlankNeighbors(blanks, mineable);
        rememberedBlanks(gui, blanks, mineable, playerTile);
        Map<Coord, NMiningOverlayMemory.TileRef> blankRefs = new HashMap<>();
        for (Coord blank : blanks) {
            NMiningOverlayMemory.TileRef ref = NMiningOverlayMemory.ofWorld(mapOf(gui), blank);
            if (ref != null) blankRefs.put(blank, ref);
        }
        for (Coord tile : greenFromFreshBlanks(blankRefs.keySet(), mineable, galleryNoGreen)) {
            wanted.put(key(tile.x, tile.y), Mark.SAFE);
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
            Gob marker = markers.get(k);
            Gob.Overlay safe = marker.findol(NMiningSafeOverlay.class);
            if (safe != null && safe.spr instanceof NMiningSafeOverlay) {
                ((NMiningSafeOverlay) safe.spr).setSources(
                        sourcesFor(Coord.of(keyX(k), keyY(k)), blankRefs));
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

    static Map<Coord, NMiningOverlayMemory.TileRef> sourcesFor(
            Coord target, Map<Coord, NMiningOverlayMemory.TileRef> blanks) {
        Map<Coord, NMiningOverlayMemory.TileRef> sources = new HashMap<>();
        for (int[] d : NEIGHBORS) {
            Coord source = target.add(d[0], d[1]);
            NMiningOverlayMemory.TileRef ref = blanks.get(source);
            if (ref != null) sources.put(source, ref);
        }
        return sources;
    }

    static boolean persistableDustNumber(boolean virtual, int value) {
        // Only a confirmed no-dust transition may persist a zero observation.
        return !virtual && value > 0;
    }

    private void revokeWarnedMemory(NGameUI gui, NumberSnapshot snapshot) {
        revokeWarningSources(snapshot.warningTiles, pendingBlanks, confirmedBlanks);
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) return;
        for (long k : snapshot.warningTiles) {
            NMiningOverlayMemory.TileRef ref = NMiningOverlayMemory.ofWorld(mapOf(gui),
                    Coord.of(keyX(k), keyY(k)));
            if (ref != null) mem.removeBlank(ref);
        }
    }

    static void revokeWarningSources(Set<Long> warnings, Map<Long, Double> pending,
                                     Set<Long> confirmed) {
        pending.keySet().removeAll(warnings);
        confirmed.removeAll(warnings);
    }

    private void rememberedBlanks(NGameUI gui, Set<Coord> blanks, Set<Coord> mineable,
                                  Coord playerTile) {
        // Legacy independent dots have no evidence to revalidate and are not trusted.
        NMiningOverlayMemory mem = resolveMemory(gui);
        if (mem == null) {
            return;
        }
        MCache map = mapOf(gui);
        for (Map.Entry<NMiningOverlayMemory.TileRef, Integer> entry : mem.numbers().entrySet()) {
            if (entry.getValue() != 0) continue;
            Coord tile = NMiningOverlayMemory.toWorld(map, entry.getKey());
            if (tile == null) {
                continue;
            }
            if (Math.abs(tile.x - playerTile.x) > RADIUS || Math.abs(tile.y - playerTile.y) > RADIUS) {
                continue;
            }
            if (blockedBlanks.contains(key(tile.x, tile.y)) || solver.getNumber(tile) > 0
                    || !Boolean.FALSE.equals(solver.mineableOrUnknown(tile.x, tile.y))) continue;
            blanks.add(tile);
            for (int[] d : NEIGHBORS) {
                Coord neighbor = tile.add(d[0], d[1]);
                if (Boolean.TRUE.equals(solver.mineableOrUnknown(neighbor.x, neighbor.y))) {
                    mineable.add(neighbor);
                }
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
