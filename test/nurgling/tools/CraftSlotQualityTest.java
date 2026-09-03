package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
        assertFalse(CraftSlotQuality.includeItem(true, true, "Stone", (String) null));
        assertFalse(CraftSlotQuality.includeItem(true, true, "Stone", (Collection<String>) null));
    }

    @Test
    void makePrepClassNameMatchesResourceAndInnerClass() {
        assertTrue(CraftSlotQuality.isMakePrepClass("nurgling.widgets.NMakewindow$MakePrep"));
        assertTrue(CraftSlotQuality.isMakePrepClass("haven.res.ui.tt.prep.MakePrep"));
        assertFalse(CraftSlotQuality.isMakePrepClass("haven.resutil.Curiosity"));
        assertFalse(CraftSlotQuality.isMakePrepClass(null));
    }

    @Test
    void categoryNamesMatchMembersNotTitle() {
        List<String> slot = CraftIngredientStock.namesFor("Clean Bird Carcass", true, null);
        assertTrue(slot.contains("Cleaned Crane"));
        assertTrue(slot.contains("Cleaned Eagle Owl"));
        assertFalse(slot.contains("Clean Bird Carcass"));
        assertTrue(CraftSlotQuality.includeItem(false, false, "Cleaned Crane", slot));
        assertTrue(CraftSlotQuality.includeItem(false, false, "Cleaned Eagle Owl", slot));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Clean Bird Carcass", slot));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Raw Crane", slot));
    }

    @Test
    void pickedMemberMatchesOnlyThatName() {
        List<String> picked = Collections.singletonList("Cleaned Crane");
        assertTrue(CraftSlotQuality.includeItem(false, false, "Cleaned Crane", picked));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Cleaned Eagle Owl", picked));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Clean Bird Carcass", picked));
    }

    @Test
    void categoryMakePrepStillRequiredWhenAnyHighlightExists() {
        List<String> slot = Arrays.asList("Cleaned Crane", "Cleaned Chicken");
        assertTrue(CraftSlotQuality.includeItem(true, true, "Cleaned Crane", slot));
        assertFalse(CraftSlotQuality.includeItem(false, true, "Cleaned Crane", slot));
        assertFalse(CraftSlotQuality.includeItem(true, true, "Clean Bird Carcass", slot));
    }

    @Test
    void mixedCategoryMembersAverageUnweighted() {
        List<String> slot = Arrays.asList("Cleaned Crane", "Cleaned Chicken");
        List<Double> qs = new ArrayList<>();
        addIfIncluded(qs, slot, false, false, "Cleaned Crane", 20.0);
        addIfIncluded(qs, slot, false, false, "Cleaned Chicken", 10.0);
        addIfIncluded(qs, slot, false, false, "Clean Bird Carcass", 99.0);
        assertEquals(15.0, CraftSlotQuality.average(qs), 1e-9);
    }

    @Test
    void namedSlotStillExactNameOnly() {
        List<String> slot = Collections.singletonList("Nettle");
        assertTrue(CraftSlotQuality.includeItem(false, false, "Nettle", slot));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Stinging Nettle", slot));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Raw Nettle", slot));
    }

    @Test
    void prefixCategoryMembersNeverSubstringMatch() {
        List<String> raw = Arrays.asList("Raw Beef", "Raw Pork");
        assertTrue(CraftSlotQuality.includeItem(false, false, "Raw Beef", raw));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Raw Chicken", raw));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Filet of Pike", raw));
        List<String> filet = Collections.singletonList("Filet of Pike");
        assertTrue(CraftSlotQuality.includeItem(false, false, "Filet of Pike", filet));
        assertFalse(CraftSlotQuality.includeItem(false, false, "Pike", filet));
    }

    private static void addIfIncluded(List<Double> qs, List<String> slot,
                                      boolean itemHasMakePrep, boolean anyMakePrep,
                                      String itemName, double quality) {
        if (CraftSlotQuality.includeItem(itemHasMakePrep, anyMakePrep, itemName, slot)) {
            qs.add(Double.valueOf(quality));
        }
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
