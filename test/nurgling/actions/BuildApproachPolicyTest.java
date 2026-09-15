package nurgling.actions;

import haven.Coord2d;
import haven.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildApproachPolicyTest {
    @Test
    void failedApproachSkipsAnExplicitGhostPosition() {
        assertEquals(
                Build.ApproachAction.SKIP_GHOST,
                Build.decideApproachAction(false, true)
        );
    }

    @Test
    void failedApproachAbortsAutomaticPlacement() {
        assertEquals(
                Build.ApproachAction.ABORT,
                Build.decideApproachAction(false, false)
        );
    }

    @Test
    void successfulApproachContinuesPlacement() {
        assertEquals(
                Build.ApproachAction.PROCEED,
                Build.decideApproachAction(true, true)
        );
    }

    @Test
    void refillSkipsCornerWalkWhenBuildZoneIsFullyVisible() {
        Pair<Coord2d, Coord2d> area = Pair.of(Coord2d.of(5050, 5050), Coord2d.of(5300, 5300));

        assertFalse(Build.requiresBuildAreaNavigation(area, Coord2d.of(5000, 5000)));
    }

    @Test
    void refillKeepsNavigationForBuildZoneOutsideVision() {
        Pair<Coord2d, Coord2d> area = Pair.of(Coord2d.of(5400, 5400), Coord2d.of(5600, 5600));

        assertTrue(Build.requiresBuildAreaNavigation(area, Coord2d.of(5000, 5000)));
    }
}
