package nurgling.actions;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFinderAvoidZoneTest {
    @Test
    void capsuleContainsPointsNearItsCoreButNotOutsideRadius() {
        PathFinder.AvoidZone zone = new PathFinder.AvoidZone(Coord2d.of(0, 0), Coord2d.of(100, 0), 10, "boar");

        assertTrue(zone.contains(Coord2d.of(50, 9)));
        assertFalse(zone.contains(Coord2d.of(50, 10)));
        assertFalse(zone.contains(Coord2d.of(111, 0)));
    }

    @Test
    void crossingRouteHasZeroCapsuleDistance() {
        PathFinder.AvoidZone zone = new PathFinder.AvoidZone(Coord2d.of(0, 0), Coord2d.of(100, 0), 10, "boar");

        assertTrue(zone.dist(Coord2d.of(50, -20), Coord2d.of(50, 20)) == 0);
        assertTrue(PathFinder.AvoidZone.anyContains(Arrays.asList(zone), Coord2d.of(10, 1)));
    }

    @Test
    void replanStormUsesOnlyEventsInsideItsTimeWindow() {
        ArrayDeque<Long> replans = new ArrayDeque<>();
        for (int i = 0; i < 8; i++) {
            assertFalse(PathFinder.noteZoneReplan(replans, i * 1_000L));
        }
        assertTrue(PathFinder.noteZoneReplan(replans, 8_000L));

        assertFalse(PathFinder.noteZoneReplan(replans, 24_001L));
    }

    @Test
    void stallLearningDependsOnAbortStateAndBlockListNotAvoidanceMode() {
        assertTrue(PathFinder.shouldLearnStall(false, new ArrayList<Coord2d>()));
        assertFalse(PathFinder.shouldLearnStall(true, new ArrayList<Coord2d>()));
        assertFalse(PathFinder.shouldLearnStall(false, null));
    }
}
