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
    void glimmerBeforeTileCompletesStillPaints() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord dug = Coord.of(10, 10);
        map.onGlimmer();
        map.tick(5.0);
        map.onTileCompleted(dug);
        Map<Coord, Integer> heat = map.visibleHeat(dug, 20, rockExcept(dug));
        assertEquals(Integer.valueOf(1), heat.get(Coord.of(13, 13)));
        assertEquals(48, heat.size());
    }

    @Test
    void oreRockNamesAreDetected() {
        assertTrue(GlimmerHeatmap.isOreRock("gfx/tiles/rocks/cassiterite"));
        assertTrue(GlimmerHeatmap.isOreRock("gfx/terobjs/bumlings/hematite"));
        assertTrue(GlimmerHeatmap.isOreRock("gfx/tiles/rocks/blackcoal"));
        assertFalse(GlimmerHeatmap.isOreRock("gfx/tiles/cave"));
        assertFalse(GlimmerHeatmap.isOreRock("gfx/tiles/rocks/granite"));
        assertFalse(GlimmerHeatmap.isOreRock(null));
    }

    @Test
    void ignoredOreCompletionDoesNotClearExistingHeat() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord glimmered = Coord.of(10, 10);
        Coord ore = Coord.of(12, 10);
        map.onTileCompleted(glimmered);
        map.onGlimmer();
        map.ignoreTile(ore);
        map.tick(GlimmerHeatmap.GLIMMER_WAIT);
        Map<Coord, Integer> heat = map.visibleHeat(ore, 20, rockExcept(glimmered, ore));
        assertEquals(Integer.valueOf(1), heat.get(Coord.of(11, 10)));
        assertEquals(Integer.valueOf(1), heat.get(Coord.of(13, 13)));
    }

    @Test
    void ignoreTileUndoesSilentExclude() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord glimmered = Coord.of(10, 10);
        Coord ore = Coord.of(12, 10);
        map.onTileCompleted(glimmered);
        map.onGlimmer();
        map.onTileCompleted(ore);
        map.tick(GlimmerHeatmap.GLIMMER_WAIT);
        Map<Coord, Integer> wiped = map.visibleHeat(ore, 20, rockExcept(glimmered, ore));
        assertFalse(wiped.containsKey(Coord.of(11, 10)));
        map.ignoreTile(ore);
        Map<Coord, Integer> heat = map.visibleHeat(ore, 20, rockExcept(glimmered, ore));
        assertEquals(Integer.valueOf(1), heat.get(Coord.of(11, 10)));
    }

    @Test
    void delayedFloorDoesNotStealEarlierGlimmer() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord a = Coord.of(10, 10);
        Coord c = Coord.of(12, 10);
        java.util.HashSet<Coord> wallsA = new java.util.HashSet<Coord>();
        wallsA.add(a);
        map.onGlimmer(wallsA);
        map.onTileCompleted(c);
        map.onTileCompleted(a);
        map.tick(GlimmerHeatmap.GLIMMER_WAIT);
        Map<Coord, Integer> heat = map.visibleHeat(a, 20, rockExcept(a, c));
        assertEquals(Integer.valueOf(1), heat.get(Coord.of(7, 10)));
        assertFalse(heat.containsKey(Coord.of(12, 13)));
        assertFalse(heat.containsKey(Coord.of(15, 10)));
    }

    @Test
    void unmatchedGlimmerExpiresThenCompletionIsSilent() {
        GlimmerHeatmap map = new GlimmerHeatmap();
        Coord dug = Coord.of(10, 10);
        map.onGlimmer();
        map.tick(GlimmerHeatmap.UNMATCHED_HOLD);
        map.onTileCompleted(dug);
        map.tick(GlimmerHeatmap.GLIMMER_WAIT);
        Map<Coord, Integer> heat = map.visibleHeat(dug, 20, rockExcept(dug));
        assertTrue(heat.isEmpty());
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
