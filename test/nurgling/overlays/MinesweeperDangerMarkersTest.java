package nurgling.overlays;

import haven.Coord;
import haven.Coord2d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
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
}
