package nurgling.tools;

import haven.res.gfx.terobjs.arch.bounds.Bounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlatWorldTest {
    @Test
    void visualCzIsZeroWhenFlat() {
        assertEquals(0.0, FlatWorld.visualCz(true, 42.5));
        assertEquals(42.5, FlatWorld.visualCz(false, 42.5));
    }

    @Test
    void overlayRelZDoesNotDrapeHiddenSlopeWhenFlat() {
        // Gob sits at visual z=0; real heightmap still has a downhill of 10.
        // Draping by (pointCz - originCz) would bury the ring; flat world must not.
        assertEquals(0f, FlatWorld.overlayRelZ(true, 10.0, 20.0));
        assertEquals(-10f, FlatWorld.overlayRelZ(false, 10.0, 20.0));
    }

    @Test
    void gobLocalPoleDropsHiddenHillWhenOriginIsAlreadyFlat() {
        // Construction/survey poles are gob-local. Under flat world the gob sits at z=0
        // while getcz is still the real hill; subtracting them hangs the stakes in the air.
        assertEquals(0f, FlatWorld.overlayRelZ(true, 40.0, 0.0));
        assertEquals(40f, FlatWorld.overlayRelZ(false, 40.0, 0.0));
        assertEquals(0f, FlatWorld.overlayRelZ(false, 40.0, 40.0));
    }

    @Test
    void surveyPlotPolesDropHiddenHillWhenGobOriginIsAlreadyFlat() {
        // gfx/terobjs/survobj posts go through Bounds.gnd: getcz - gob.z.
        // Flat world already puts the gob at z=0, so the real hill must not be added back.
        assertEquals(0f, Bounds.poleLocalZ(true, 40.0, 0.0));
        assertEquals(40f, Bounds.poleLocalZ(false, 40.0, 0.0));
        assertEquals(0f, Bounds.poleLocalZ(false, 40.0, 40.0));
    }

    @Test
    void flattenBillboardLocalZUndoesHiddenGobHeightWhenFlat() {
        // Identity location: leave the overlay's own offset alone.
        assertEquals(4f, FlatWorld.flattenBillboardLocalZ(true, 4f, 1f, 0f));
        // Pure translate by the hidden hill: object Z that lands at world localZ.
        assertEquals(-36f, FlatWorld.flattenBillboardLocalZ(true, 4f, 1f, 40f));
        // Non-flat: never rewrite — ordinary relief keeps parenting as-is.
        assertEquals(4f, FlatWorld.flattenBillboardLocalZ(false, 4f, 1f, 40f));
        // Uniform scale after a translate of 40 (m14=20, m10=0.5) still lands at world 4.
        assertEquals(-32f, FlatWorld.flattenBillboardLocalZ(true, 4f, 0.5f, 20f));
    }
}
