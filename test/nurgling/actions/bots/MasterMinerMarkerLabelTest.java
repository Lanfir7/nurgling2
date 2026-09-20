package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerMarkerLabelTest {

    @Test
    void mapLabelStarsOnlyMasonryCappedResults() {
        assertEquals("q60*", MasterMiner.markerLabel(60.0, 60, "Stone"));
        assertEquals("q59*", MasterMiner.markerLabel(59.0, 60, "Cat Gold"));
        assertEquals("q75*", MasterMiner.markerLabel(75.0, 60, "Quarryartz"));
        assertEquals("q62", MasterMiner.markerLabel(62.0, 60, "Stone"));
    }

    @Test
    void cappedLabelReplacesAnOlderEqualQualityLabelWithoutTheStar() {
        assertTrue(MasterMiner.shouldUpdateMarker(60.0, "q60*", 60.0, "q60"));
        assertFalse(MasterMiner.shouldUpdateMarker(60.0, "q60", 60.0, "q60*"));
        assertTrue(MasterMiner.shouldUpdateMarker(61.0, "q61", 60.0, "q60*"));
    }
}
