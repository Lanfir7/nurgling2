package nurgling.planning;

import haven.Coord2d;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldCaptureTest {
    @Test void rejectsUnboundedAndInvalidCaptureBeforeLookingAtMap() {
        assertFalse(WorldCapture.validBounds(new Coord2d(Double.NaN, 0), new Coord2d(11, 11)));
        assertFalse(WorldCapture.validBounds(Coord2d.z, new Coord2d(Double.POSITIVE_INFINITY, 11)));
        assertFalse(WorldCapture.validBounds(Coord2d.z, Coord2d.z));
        assertFalse(WorldCapture.validBounds(new Coord2d(11, 0), Coord2d.z));
        assertFalse(WorldCapture.validBounds(Coord2d.z, new Coord2d(11 * 513, 11)));
        assertFalse(WorldCapture.validBounds(new Coord2d(1e20, 1e20), new Coord2d(1e20 + 100000, 1e20 + 100000)));
    }

    @Test void acceptsNegativeAndCrossGridCaptureWithinCap() {
        assertTrue(WorldCapture.validBounds(new Coord2d(-1111, -22), new Coord2d(22, 33)));
        assertTrue(WorldCapture.validBounds(new Coord2d(-11 * 512, -11), new Coord2d(0, 0)));
    }

    @Test void placementAngleQuantizationDoesNotDuplicateBuiltGhost() {
        double plannedAngle = Math.PI / 12;
        double receivedAngle = Math.round(plannedAngle * 32768 / Math.PI) * Math.PI / 32768;
        PlannerPreset.Ghost built = new PlannerPreset.Ghost("gfx/terobjs/test", null, 0, 0, receivedAngle);
        PlannerPreset.Ghost planned = new PlannerPreset.Ghost(built.res, null, 0, 0, plannedAngle);
        Map<PlannerPreset.Ghost, double[]> extents = new IdentityHashMap<>();
        extents.put(built, new double[]{-5.5, -5.5, 5.5, 5.5});
        extents.put(planned, new double[]{-5.5, -5.5, 5.5, 5.5});
        assertEquals(1, WorldCapture.build("quantized", Collections.singletonList(built), extents,
                Collections.singletonList(planned)).ghostCount());
    }

    @Test void combinesOnlyMatchingPositionAndFacingAndCopiesSpriteData() {
        PlannerPreset.Ghost built = new PlannerPreset.Ghost("gfx/terobjs/test", new byte[]{7}, -11, -11, 0);
        PlannerPreset.Ghost same = new PlannerPreset.Ghost(built.res, null, -11, -11, 2 * Math.PI);
        PlannerPreset.Ghost rotated = new PlannerPreset.Ghost(built.res, null, -11, -11, Math.PI / 2);
        Map<PlannerPreset.Ghost, double[]> extents = new IdentityHashMap<>();
        extents.put(built, new double[]{-16.5, -16.5, -5.5, -5.5});
        // Supply extents for the rotated ghost too, avoiding a server resource in this headless test.
        extents.put(rotated, new double[]{-16.5, -16.5, -5.5, -5.5});
        PlannerPreset result = WorldCapture.build("test", Collections.singletonList(built), extents,
                Arrays.asList(same, rotated));
        assertEquals(2, result.ghostCount());
        assertEquals(Math.PI / 2, result.layers.get(0).ghosts.get(1).angle);
        built.sdt[0] = 9;
        rotated.x = 99;
        assertEquals(7, result.layers.get(0).ghosts.get(0).sdt[0]);
        assertNotEquals(99, result.layers.get(0).ghosts.get(1).x);
    }
}
