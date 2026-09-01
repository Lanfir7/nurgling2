package nurgling.widgets.compass;

import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCompassSettingsTest {
    @Test
    void freshConfigEnablesBarAndDisablesLegacyPointers() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            assertTrue(NCompassSettings.showBar());
            assertFalse(NCompassSettings.showLegacyPointers());
        } finally {
            NConfig.current = previous;
        }
    }

    @Test
    void nullConfigIsSafeAndOff() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = null;
            assertFalse(NCompassSettings.showBar());
            assertFalse(NCompassSettings.showLegacyPointers());
        } finally {
            NConfig.current = previous;
        }
    }
}
