package nurgling.widgets.compass;

import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertTrue(NCompassSettings.showQuests());
            assertTrue(NCompassSettings.showParty());
            assertTrue(NCompassSettings.showDatabasePeers());
            assertTrue(NCompassSettings.showNearbyPlayers());
            assertFalse(NCompassSettings.showAnimals());
            assertTrue(NCompassSettings.showCombatTargets());
            assertEquals(75, NCompassSettings.backgroundOpacity());
            assertEquals(191, NCompassSettings.backgroundAlpha());
        } finally {
            NConfig.current = previous;
        }
    }

    @Test
    void backgroundOpacityIsClampedToValidPercentage() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            NConfig.set(NConfig.Key.compassBackgroundOpacity, -20);
            assertEquals(0, NCompassSettings.backgroundOpacity());
            assertEquals(0, NCompassSettings.backgroundAlpha());

            NConfig.set(NConfig.Key.compassBackgroundOpacity, 180);
            assertEquals(100, NCompassSettings.backgroundOpacity());
            assertEquals(255, NCompassSettings.backgroundAlpha());
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
            assertFalse(NCompassSettings.showQuests());
            assertFalse(NCompassSettings.showParty());
            assertFalse(NCompassSettings.showDatabasePeers());
            assertFalse(NCompassSettings.showNearbyPlayers());
            assertFalse(NCompassSettings.showAnimals());
            assertFalse(NCompassSettings.showCombatTargets());
        } finally {
            NConfig.current = previous;
        }
    }
}
