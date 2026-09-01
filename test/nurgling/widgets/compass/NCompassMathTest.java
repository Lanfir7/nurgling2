package nurgling.widgets.compass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCompassMathTest {
    private static final double EPS = 0.000001;

    @Test
    void cameraLooksFromItsOrbitPositionTowardThePlayer() {
        assertEquals(-Math.PI, NCompassMath.cameraHeading(0.0), EPS);
        assertEquals(-Math.PI / 2, NCompassMath.cameraHeading(Math.PI / 2), EPS);
    }

    @Test
    void headingTargetProjectsToCenter() {
        NCompassMath.Projection p = NCompassMath.project(Math.PI, 0.0, 500);
        assertEquals(NCompassMath.Region.FRONT, p.region);
        assertEquals(250, p.x);
    }

    @Test
    void exactSectorEdgesRemainVisible() {
        NCompassMath.Projection left = NCompassMath.project(Math.PI / 2, 0.0, 500);
        NCompassMath.Projection right = NCompassMath.project(-Math.PI / 2, 0.0, 500);
        assertEquals(NCompassMath.Region.FRONT, left.region);
        assertEquals(0, left.x);
        assertEquals(NCompassMath.Region.FRONT, right.region);
        assertEquals(500, right.x);
    }

    @Test
    void targetsJustPastSectorEdgesBecomeRearTargets() {
        assertEquals(NCompassMath.Region.REAR_LEFT,
                NCompassMath.project((Math.PI / 2) - 0.01, 0.0, 500).region);
        assertEquals(NCompassMath.Region.REAR_RIGHT,
                NCompassMath.project((-Math.PI / 2) + 0.01, 0.0, 500).region);
    }

    @Test
    void targetAcrossWrapStaysNearCenter() {
        NCompassMath.Projection p = NCompassMath.project(-Math.PI + 0.01, 0.0, 1000);
        assertEquals(NCompassMath.Region.FRONT, p.region);
        assertTrue(p.x > 500 && p.x < 510);
    }

    @Test
    void oppositeTargetIsAssignedDeterministicallyToRearLeft() {
        assertEquals(NCompassMath.Region.REAR_LEFT,
                NCompassMath.project(0.0, 0.0, 500).region);
    }
}
