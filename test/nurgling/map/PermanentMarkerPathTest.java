package nurgling.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermanentMarkerPathTest {
    @Test
    void cavePassageVanillaIconsAreReady() {
        assertTrue(PermanentMarkerPath.isReadyMinimapResource("mm/up"));
        assertTrue(PermanentMarkerPath.isReadyMinimapResource("mm/down"));
        assertFalse(PermanentMarkerPath.isReadyMinimapResource("gfx/invobjs/herbs/salvia"));
        assertFalse(PermanentMarkerPath.isReadyMinimapResource(null));
    }

    @Test
    void cavePassageMustNotProbeRemotePaths() {
        assertFalse(PermanentMarkerPath.shouldProbeRemotePaths("Cave Passage", "mm/down"));
        assertFalse(PermanentMarkerPath.shouldProbeRemotePaths("Cave Passage", "mm/up"));
        assertFalse(PermanentMarkerPath.shouldProbeRemotePaths("Cave Passage", "gfx/invobjs/herbs/salvia"));
        assertTrue(PermanentMarkerPath.isCavePassage("Cave Passage"));
    }

    @Test
    void otherWrongIconsMayStillBeCorrected() {
        assertTrue(PermanentMarkerPath.shouldProbeRemotePaths("Wine Glance", "gfx/invobjs/wrong"));
        assertFalse(PermanentMarkerPath.shouldProbeRemotePaths("Wine Glance", "gfx/terobjs/mm/wineglance"));
    }
}
