package nurgling.overlays;

import haven.Coord;
import haven.Coord2d;
import nurgling.tools.NNoticeLog;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinesweeperDangerMarkersTest {

    @Test
    void greenOnlyOnMineableNeighborsOfFreshBlank() {
        Coord blank = new Coord(5, 5);
        Set<Coord> mineable = new HashSet<>();
        mineable.add(new Coord(6, 5));
        mineable.add(new Coord(5, 6));
        mineable.add(new Coord(4, 4));

        Set<Coord> green = MinesweeperDangerMarkers.greenFromFreshBlanks(
                Set.of(blank), mineable);

        assertEquals(Set.of(new Coord(6, 5), new Coord(5, 6), new Coord(4, 4)), green);
        assertFalse(green.contains(blank));
        assertFalse(green.contains(new Coord(5, 4)));
    }

    @Test
    void noGreenWhenBlankHasNoMineableNeighbors() {
        Set<Coord> green = MinesweeperDangerMarkers.greenFromFreshBlanks(
                Set.of(new Coord(0, 0)), Set.of());
        assertTrue(green.isEmpty());
    }

    @Test
    void caveGalleryNoticeDiscardsPendingAndConfirmedBlankTransitions() {
        NNoticeLog notices = new NNoticeLog();
        long noticeMark = notices.seq();
        Map<Long, Double> pending = new HashMap<>();
        pending.put(10L, 0.4);
        Set<Long> confirmed = new HashSet<>(Set.of(20L));

        notices.add("You opened a natural cave gallery.");

        assertTrue(MinesweeperDangerMarkers.discardGalleryBlankTransitions(
                notices, noticeMark, pending, confirmed));
        assertTrue(pending.isEmpty());
        assertTrue(confirmed.isEmpty());
    }

    @Test
    void unrelatedNoticeKeepsOrdinaryBlankTransitions() {
        NNoticeLog notices = new NNoticeLog();
        long noticeMark = notices.seq();
        Map<Long, Double> pending = new HashMap<>();
        pending.put(10L, 0.4);
        Set<Long> confirmed = new HashSet<>(Set.of(20L));

        notices.add("You mine some stone.");

        assertFalse(MinesweeperDangerMarkers.discardGalleryBlankTransitions(
                notices, noticeMark, pending, confirmed));
        assertEquals(Map.of(10L, 0.4), pending);
        assertEquals(Set.of(20L), confirmed);
    }

    @Test
    void galleryNoticeSuppressesNewTransitionInSameTick() {
        Map<Long, Double> pending = new HashMap<>();
        Set<Long> confirmed = new HashSet<>();

        assertFalse(MinesweeperDangerMarkers.recordMinedTileTransition(
                true, false, true, 10L, pending, confirmed));
        assertTrue(pending.isEmpty());

        assertTrue(MinesweeperDangerMarkers.recordMinedTileTransition(
                true, false, false, 10L, pending, confirmed));
        assertEquals(Map.of(10L, 0.0), pending);
    }

    @Test
    void gallerySuppressStartsOnNoticeAndOutlivesConsumedMark() {
        assertEquals(2.0, MinesweeperDangerMarkers.nextGallerySuppress(
                true, false, 0.0, 0.05, 2.0), 1e-9);

        NNoticeLog notices = new NNoticeLog();
        long mark = notices.seq();
        Map<Long, Double> pending = new HashMap<>();
        Set<Long> confirmed = new HashSet<>();
        notices.add("You opened a natural cave gallery.");

        boolean noticed = MinesweeperDangerMarkers.discardGalleryBlankTransitions(
                notices, mark, pending, confirmed);
        double remaining = MinesweeperDangerMarkers.nextGallerySuppress(
                noticed, false, 0.0, 0.05, 2.0);
        mark = notices.seq();

        noticed = MinesweeperDangerMarkers.discardGalleryBlankTransitions(
                notices, mark, pending, confirmed);
        remaining = MinesweeperDangerMarkers.nextGallerySuppress(
                noticed, true, remaining, 0.05, 2.0);

        assertFalse(noticed);
        assertEquals(2.0, remaining, 1e-9);
        assertFalse(MinesweeperDangerMarkers.recordMinedTileTransition(
                true, false, remaining > 0, 99L, pending, confirmed));
        assertTrue(pending.isEmpty());
    }

    @Test
    void gallerySuppressCountsDownWhenIdleThenAllowsOrdinaryBlanks() {
        double remaining = MinesweeperDangerMarkers.nextGallerySuppress(
                false, false, 2.0, 0.5, 2.0);
        assertEquals(1.5, remaining, 1e-9);

        remaining = MinesweeperDangerMarkers.nextGallerySuppress(
                false, false, 0.1, 0.5, 2.0);
        assertEquals(0.0, remaining, 1e-9);

        Map<Long, Double> pending = new HashMap<>();
        Set<Long> confirmed = new HashSet<>();
        assertTrue(MinesweeperDangerMarkers.recordMinedTileTransition(
                true, false, remaining > 0, 10L, pending, confirmed));
        assertEquals(Map.of(10L, 0.0), pending);
    }

    @Test
    void playerTileIsCapturedFromSingleLookup() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Coord2d> position = () -> calls.getAndIncrement() == 0
                ? Coord2d.of(110, 220)
                : null;

        Coord tile = MinesweeperDangerMarkers.snapshotPlayerTile(position);

        assertEquals(Coord.of(10, 20), tile);
        assertEquals(1, calls.get());
    }

    @Test
    void numberSnapshotIsReusedForThreeTenthsOfASecond() {
        AtomicInteger scans = new AtomicInteger();
        MinesweeperDangerMarkers.TimedSnapshot<Integer> cache =
                MinesweeperDangerMarkers.newNumberSnapshotCache();

        MinesweeperDangerMarkers.SnapshotUpdate<Integer> first =
                cache.update(0, scans::incrementAndGet);
        MinesweeperDangerMarkers.SnapshotUpdate<Integer> cached =
                cache.update(0.299, scans::incrementAndGet);
        MinesweeperDangerMarkers.SnapshotUpdate<Integer> refreshed =
                cache.update(0.002, scans::incrementAndGet);

        assertTrue(first.refreshed);
        assertEquals(1, first.value);
        assertFalse(cached.refreshed);
        assertEquals(1, cached.value);
        assertTrue(refreshed.refreshed);
        assertEquals(2, refreshed.value);
        assertEquals(2, scans.get());
    }

    @Test
    void syncDoesNotRegisterSolverDangerTilesAsOverlayMarks() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/overlays/MinesweeperDangerMarkers.java"));
        int syncAt = src.indexOf("private void sync(");
        int nextAt = src.indexOf("private void rememberedGreens(");
        assertTrue(syncAt >= 0 && nextAt > syncAt, "sync() must remain immediately before rememberedGreens");
        String sync = src.substring(syncAt, nextAt);

        assertFalse(sync.contains("dangerTiles()"),
                "sync must not place DANGER marks from solver.dangerTiles()");
        assertFalse(sync.contains("Mark.DANGER"),
                "sync must not register Mark.DANGER overlay crosses");

        assertTrue(sync.contains("greenFromFreshBlanks"),
                "green circles from blank-mined neighbors must remain");
        assertTrue(sync.contains("Mark.SAFE"),
                "SAFE marks must remain in sync");
        assertTrue(sync.contains("rememberedGreens"),
                "remembered green persistence path must remain");
        assertTrue(src.contains("NMiningNumber"),
                "number overlays must remain");
        assertTrue(src.contains("NMiningSafeOverlay"),
                "green safe overlay must remain");
        assertTrue(src.contains("public void tick("),
                "MinesweeperDangerMarkers tick must stay wired");
        int tickAt = src.indexOf("public void tick(");
        int restoreAt = src.indexOf("public void restoreNow()");
        assertTrue(tickAt >= 0 && restoreAt > tickAt, "tick() must remain immediately before restoreNow");
        String tick = src.substring(tickAt, restoreAt);
        assertTrue(tick.contains("nextGallerySuppress"),
                "gallery suppress must outlive the notice tick");
        assertTrue(tick.contains("gallerySuppressLeft"),
                "gallery suppress remaining time must be latched");
    }
}
