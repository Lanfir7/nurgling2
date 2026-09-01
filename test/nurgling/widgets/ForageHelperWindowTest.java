package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForageHelperWindowTest {
    @Test
    void yesAndNoSeasonsUseColoredSymbols() {
        assertEquals("✓", ForageSeasonPresentation.glyph("Y"));
        assertEquals(new Color(90, 220, 105), ForageSeasonPresentation.color("Y"));
        assertEquals("✕", ForageSeasonPresentation.glyph("N"));
        assertEquals(new Color(235, 85, 85), ForageSeasonPresentation.color("N"));
    }

    @Test
    void seasonSymbolsUseDedicatedFontThatCanDisplayBothMarks() {
        Font font = ForageSeasonPresentation.symbolFont();

        assertTrue(font.canDisplay('✓'));
        assertTrue(font.canDisplay('✕'));
    }

    @Test
    void conditionalYesUsesYellowCheckWithoutLeavingYText() {
        assertEquals("✓", ForageSeasonPresentation.glyph("(Y)"));
        assertEquals(new Color(235, 205, 95), ForageSeasonPresentation.color("(Y)"));
    }

    @Test
    void bonusAndUnknownSeasonsKeepTheirWikiNotation() {
        assertEquals("+", ForageSeasonPresentation.glyph("+"));
        assertEquals("?", ForageSeasonPresentation.glyph("?"));
    }

    @Test
    void headerEndsWithTerrainAndHasNoRemarkColumn() {
        assertEquals(9, ForageHelperTableLayout.columnHeaders().size());
        assertEquals("Terrain", ForageHelperTableLayout.columnHeaders().get(8));
        assertFalse(ForageHelperTableLayout.columnHeaders().contains("Remark"));
    }

    @Test
    void onlyTerrainAreaIsHandledAsTerrainClick() {
        int start = ForageHelperTableLayout.terrainColumnStart();

        assertFalse(ForageHelperTableLayout.isTerrainColumn(start - 1, start + 180));
        assertTrue(ForageHelperTableLayout.isTerrainColumn(start, start + 180));
        assertTrue(ForageHelperTableLayout.isTerrainColumn(start + 179, start + 180));
        assertFalse(ForageHelperTableLayout.isTerrainColumn(start + 180, start + 180));
    }
}
