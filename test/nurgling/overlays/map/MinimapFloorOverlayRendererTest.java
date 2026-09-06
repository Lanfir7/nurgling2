package nurgling.overlays.map;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapFloorOverlayRendererTest {
    private final NConfig previous = NConfig.current;

    @AfterEach
    void restoreCurrent() {
        NConfig.current = previous;
    }

    @Test
    void floorOverlayDefaultsToEnabledAtMaxAlpha() {
        NConfig.current = new NConfig();

        assertTrue(MinimapFloorOverlayRenderer.enabled());
        assertEquals(255, MinimapFloorOverlayRenderer.overlayAlpha());
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.floorOverlayEnable));
        assertEquals(255, ((Number) NConfig.get(NConfig.Key.floorOverlayAlpha)).intValue());
    }

    @Test
    void overlayAlphaStaysMaxEvenIfSavedConfigIsLower() {
        NConfig.current = new NConfig();
        NConfig.set(NConfig.Key.floorOverlayAlpha, 120);

        assertEquals(255, MinimapFloorOverlayRenderer.overlayAlpha());
    }

    @Test
    void enabledStillRespectsHudToggle() {
        NConfig.current = new NConfig();
        NConfig.set(NConfig.Key.floorOverlayEnable, false);

        assertFalse(MinimapFloorOverlayRenderer.enabled());
    }
}
