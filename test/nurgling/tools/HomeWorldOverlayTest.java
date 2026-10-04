package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeWorldOverlayTest {
    @Test
    void eachCheckboxHidesOnlyItsOwnLayer() {
        assertTrue(HomeWorldOverlay.suppressTerritoryLayer(true, false, true,
                Arrays.asList("cplot", "own"), false));
        assertFalse(HomeWorldOverlay.suppressTerritoryLayer(true, false, true,
                Arrays.asList("vlg", "own"), false));
        assertTrue(HomeWorldOverlay.suppressTerritoryLayer(false, true, true,
                Arrays.asList("vlg", "own"), false));
        assertFalse(HomeWorldOverlay.suppressTerritoryLayer(false, true, true,
                Arrays.asList("cplot", "own"), false));
    }

    @Test
    void keepsForeignClaimsAndRealms() {
        assertFalse(HomeWorldOverlay.suppressTerritoryLayer(true, true, true,
                Collections.singletonList("cplot"), false));
        assertFalse(HomeWorldOverlay.suppressTerritoryLayer(true, true, true,
                Arrays.asList("realm", "own"), true));
        assertFalse(HomeWorldOverlay.suppressTerritoryLayer(true, true, false,
                Arrays.asList("cplot", "own"), true));
        assertFalse(HomeWorldOverlay.suppressTerritoryLayer(false, false, true,
                Arrays.asList("vlg", "own"), true));
    }

    @Test
    void hidesUntaggedLayerOnlyUnderThePlayer() {
        assertTrue(HomeWorldOverlay.suppressTerritoryLayer(false, true, true,
                Collections.singletonList("vlg"), true));
        assertFalse(HomeWorldOverlay.suppressTerritoryLayer(true, false, true,
                Collections.singletonList("cplot"), false));
    }

    @Test
    void miningOverlayLeavesHomeLandWhenItsCheckboxIsOff() {
        assertTrue(HomeWorldOverlay.suppressMining(false, true));
        assertFalse(HomeWorldOverlay.suppressMining(true, true));
        assertFalse(HomeWorldOverlay.suppressMining(null, true));
        assertFalse(HomeWorldOverlay.suppressMining(false, false));
    }
}
