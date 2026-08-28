package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathFinderApproachWaypointTest {
    @Test
    void dummyDoesNotAdjustIntermediateWaypoint() {
        assertFalse(PathFinder.shouldAdjustApproachWaypoint(false, true, true));
    }

    @Test
    void dummyAdjustsFinalWaypoint() {
        assertTrue(PathFinder.shouldAdjustApproachWaypoint(true, false, true));
    }

    @Test
    void hardModeAdjustsFinalWaypointWithoutDummy() {
        assertTrue(PathFinder.shouldAdjustApproachWaypoint(true, true, false));
    }
}
