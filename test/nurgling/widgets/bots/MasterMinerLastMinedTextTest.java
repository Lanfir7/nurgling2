package nurgling.widgets.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerLastMinedTextTest {

    @Test
    void captionEndsWithColonAndStaysOnItsOwnLine() {
        String caption = MasterMinerWnd.lastMinedCaptionText();
        assertTrue(caption.endsWith(":"));
        assertFalse(caption.contains("\n"));
        assertFalse(caption.contains("Microlite"));
    }

    @Test
    void lastMinedShowsHandAndWallQualityOnTheValueLine() {
        String value = MasterMinerWnd.lastMinedValueText("Microlite", 60.0, 59.1);
        assertTrue(value.startsWith("Microlite"));
        assertTrue(value.contains("60.00"));
        assertTrue(value.contains("59.10"));
        assertFalse(value.contains(MasterMinerWnd.lastMinedCaptionText()));
        assertFalse(value.contains("\n"));
    }

    @Test
    void emptyLastMinedValueIsDash() {
        assertEquals("-", MasterMinerWnd.lastMinedValueText(null, 1, 2));
        assertEquals("-", MasterMinerWnd.lastMinedValueText("", 1, 2));
    }

    @Test
    void qualityLineKeepsHandsWallAndAltTool() {
        assertEquals("Microlite: 60.00 [59.10] (61.25)",
                MasterMinerWnd.qualityLineText("Microlite", 60.0, 59.1, 61.25));
        assertEquals("Shell: 114.00 [114.00]",
                MasterMinerWnd.qualityLineText("Shell", 114.0, 114.0, null));
    }
}
