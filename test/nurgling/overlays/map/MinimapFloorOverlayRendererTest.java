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
    void floorOverlayDefaultsToEnabledAtTranslucentAlpha() {
        NConfig.current = new NConfig();

        assertTrue(MinimapFloorOverlayRenderer.enabled());
        assertEquals(120, MinimapFloorOverlayRenderer.overlayAlpha());
        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.floorOverlayEnable));
        assertEquals(120, ((Number) NConfig.get(NConfig.Key.floorOverlayAlpha)).intValue());
    }

    @Test
    void overlayAlphaReadsConfigAndClamps() {
        NConfig.current = new NConfig();
        NConfig.set(NConfig.Key.floorOverlayAlpha, 180);
        assertEquals(180, MinimapFloorOverlayRenderer.overlayAlpha());

        NConfig.set(NConfig.Key.floorOverlayAlpha, 10);
        assertEquals(32, MinimapFloorOverlayRenderer.overlayAlpha());

        NConfig.set(NConfig.Key.floorOverlayAlpha, 300);
        assertEquals(255, MinimapFloorOverlayRenderer.overlayAlpha());
    }

    @Test
    void enabledStillRespectsHudToggle() {
        NConfig.current = new NConfig();
        NConfig.set(NConfig.Key.floorOverlayEnable, false);

        assertFalse(MinimapFloorOverlayRenderer.enabled());
    }
}
