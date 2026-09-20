package nurgling.widgets.nsettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningMasterySettingsDefaultsTest {

    @Test
    void onlyRequestedMineralsDefaultToEnabledAtTheMarkerThreshold() {
        assertFalse(MiningMasterySettings.defaultMarkerEnabled("Granite"));
        assertTrue(MiningMasterySettings.defaultMarkerEnabled("Onyx"));
        assertTrue(MiningMasterySettings.defaultMarkerEnabled("Feldspar"));
        assertTrue(MiningMasterySettings.defaultMarkerEnabled("Cassiterite"));
        assertTrue(MiningMasterySettings.defaultMarkerEnabled("Flint"));
        assertTrue(MiningMasterySettings.defaultMarkerEnabled("Quartz"));
        assertTrue(MiningMasterySettings.defaultMarkerEnabled("Quarryartz"));
        assertTrue(MiningMasterySettings.defaultMarkerEnabled("Rock Salt"));
        assertTrue(MiningMasterySettings.defaultMarkerEnabled("Black Coal"));
        assertEquals(10.0, MiningMasterySettings.defaultMarkerThreshold("Cassiterite"));
        assertEquals(10.0, MiningMasterySettings.defaultMarkerThreshold("Flint"));
        assertEquals(10.0, MiningMasterySettings.defaultMarkerThreshold("Quartz"));
        assertEquals(10.0, MiningMasterySettings.defaultMarkerThreshold("Quarryartz"));
        assertEquals(10.0, MiningMasterySettings.defaultMarkerThreshold("Rock Salt"));
        assertEquals(10.0, MiningMasterySettings.defaultMarkerThreshold("Black Coal"));
        assertTrue(Double.isNaN(MiningMasterySettings.defaultMarkerThreshold("Granite")));
        assertEquals(10.0, MiningMasterySettings.defaultMarkerThreshold("Onyx"));
        assertEquals(10.0, MiningMasterySettings.defaultMarkerThreshold("Feldspar"));
    }
}
