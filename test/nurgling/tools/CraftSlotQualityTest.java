package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftSlotQualityTest {

    @Test
    void meanOfSlotAveragesIsUnweightedByRecipeCount() {
        // Two slots, one of them is count=2 in the recipe — still one average each.
        Double mean = CraftSlotQuality.meanOfSlotAverages(Arrays.asList(10.0, 20.0));
        assertEquals(15.0, mean, 1e-9);
    }

    @Test
    void meanSkipsEmptySlotsAndDoesNotInventZero() {
        assertEquals(15.0, CraftSlotQuality.meanOfSlotAverages(Arrays.asList(10.0, null, 20.0)), 1e-9);
        assertNull(CraftSlotQuality.meanOfSlotAverages(Arrays.asList(null, null)));
        assertNull(CraftSlotQuality.meanOfSlotAverages(Collections.emptyList()));
        assertNull(CraftSlotQuality.meanOfSlotAverages(null));
    }

    @Test
    void slotAverageSkipsMissingQualityAndStaysBlankWhenNone() {
        assertEquals(20.0, CraftSlotQuality.average(Arrays.asList(10.0, 30.0)), 1e-9);
        assertNull(CraftSlotQuality.average(Arrays.asList(null, null)));
        assertNull(CraftSlotQuality.qualityOf(null));
        assertNull(CraftSlotQuality.qualityOf(0f));
        assertNull(CraftSlotQuality.qualityOf(-1f));
        assertEquals(12.5, CraftSlotQuality.qualityOf(12.5f), 1e-4);
    }

    @Test
    void includeItemPrefersMakePrepWhenAnyHighlightedExist() {
        assertTrue(CraftSlotQuality.includeItem(true, true, "Stone", "Stone"));
        assertFalse(CraftSlotQuality.includeItem(false, true, "Stone", "Stone"));
        assertFalse(CraftSlotQuality.includeItem(true, true, "Branch", "Stone"));
        // Fallback: no MakePrep on inventory → name match is enough.
        assertTrue(CraftSlotQuality.includeItem(false, false, "Stone", "Stone"));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Branch", "Stone"));
        assertFalse(CraftSlotQuality.includeItem(true, true, null, "Stone"));
        assertFalse(CraftSlotQuality.includeItem(true, true, "Stone", null));
    }

    @Test
    void makePrepClassNameMatchesResourceAndInnerClass() {
        assertTrue(CraftSlotQuality.isMakePrepClass("nurgling.widgets.NMakewindow$MakePrep"));
        assertTrue(CraftSlotQuality.isMakePrepClass("haven.res.ui.tt.prep.MakePrep"));
        assertFalse(CraftSlotQuality.isMakePrepClass("haven.resutil.Curiosity"));
        assertFalse(CraftSlotQuality.isMakePrepClass(null));
    }

    @Test
    void autoSearchUsesSelectedIngredientName() {
        assertEquals("Branch", CraftSlotQuality.slotMatchName("Wooden Block", "Branch"));
        assertEquals("Stone", CraftSlotQuality.slotMatchName("Stone", null));
    }

    @Test
    void packedHeightAddsLineOnceAndDoesNotStack() {
        int line = CraftSlotQuality.LINE;
        assertEquals(112, CraftSlotQuality.packedHeight(100, 0, line));
        assertEquals(112, CraftSlotQuality.packedHeight(100, 112, line));
        // Children already filled to the previous packed size — do not add LINE again.
        assertEquals(112, CraftSlotQuality.packedHeight(112, 112, line));
        // Real content grew: still exactly one line, not two.
        assertEquals(132, CraftSlotQuality.packedHeight(120, 112, line));
        assertEquals(CraftSlotQuality.packedHeight(100, 50, line),
                CraftSlotQuality.packedHeight(100, 50, line));
    }
}
