package nurgling.overlays;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NObjectLabelSettingsTest {
    private final NConfig previous = NConfig.current;

    @AfterEach
    void restoreCurrent() {
        NConfig.current = previous;
    }

    @Test
    void defaultsShowBothLabelSourcesWithLowerHalfTransparentPlate() {
        NConfig.current = new NConfig();

        NObjectLabelSettings settings = NObjectLabelSettings.current();

        assertTrue(settings.enabled);
        assertTrue(settings.iconSigns);
        assertTrue(settings.parchments);
        assertEquals(12, settings.fontSize);
        assertEquals(5, settings.height);
        assertEquals(50, settings.backgroundOpacity);
    }

    @Test
    void clampsPersistedNumericValuesToSupportedSliderRanges() {
        NConfig.current = new NConfig();
        NConfig.set(NConfig.Key.objectLabelFontSize, 99);
        NConfig.set(NConfig.Key.objectLabelHeight, -4);
        NConfig.set(NConfig.Key.objectLabelBackgroundOpacity, 140);

        NObjectLabelSettings settings = NObjectLabelSettings.current();

        assertEquals(24, settings.fontSize);
        assertEquals(0, settings.height);
        assertEquals(100, settings.backgroundOpacity);
    }

    @Test
    void changingSettingsAdvancesTheOverlayRefreshRevision() {
        long before = NObjectLabelSettings.revision();

        NObjectLabelSettings.changed();

        assertTrue(NObjectLabelSettings.revision() > before);
    }
}
