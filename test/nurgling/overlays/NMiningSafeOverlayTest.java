package nurgling.overlays;

import nurgling.conf.NMiningOverlayMemory.TileRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMiningSafeOverlayTest {
    @Test
    void rawDustIsAWarningBeforeNumberOverlayIsAttached() {
        assertTrue(NMiningSafeOverlay.dustWarning("gfx/fx/cavewarn", null));
        assertTrue(NMiningSafeOverlay.dustWarning(null, 2));
        assertTrue(NMiningSafeOverlay.dustWarning(null, 0));
        assertFalse(NMiningSafeOverlay.dustWarning("gfx/fx/other", null));
    }
    @Test
    void lateWarningRevokesBlankEvidence() {
        TileRef source = new TileRef(100, 10, 20);
        assertTrue(NMiningSafeOverlay.sourceConfirmed(source, source, false, false));
        assertFalse(NMiningSafeOverlay.sourceConfirmed(source, source, false, true));
    }

    @Test
    void unloadedWallAndDifferentCaveCannotSupplySafety() {
        TileRef source = new TileRef(100, 10, 20);
        assertFalse(NMiningSafeOverlay.sourceConfirmed(source, source, null, false));
        assertFalse(NMiningSafeOverlay.sourceConfirmed(source, source, true, false));
        assertFalse(NMiningSafeOverlay.sourceConfirmed(source, new TileRef(200, 10, 20), false, false));
        assertFalse(NMiningSafeOverlay.sourceConfirmed(source, null, false, false));
    }
}
