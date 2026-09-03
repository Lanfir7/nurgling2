package nurgling;

import haven.res.ui.tt.attrmod.intattr;
import haven.res.ui.tt.attrmod.normattr;
import haven.res.ui.tt.attrmod.pmattr;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayAttrModFormatTest {

    @Test
    void learningAbilityPercentPointsFiveFormatsAsPlusFivePercent() {
        assertEquals("+5%", OverlayAttrModFormat.formatModValue(5, true));
    }

    @Test
    void intStyleFlatThreeStaysPlusThree() {
        assertEquals("+3", OverlayAttrModFormat.formatModValue(3, false));
    }

    @Test
    void fractionZeroPointFifteenStillPlusFifteenPercent() {
        assertEquals("+15%", OverlayAttrModFormat.formatModValue(0.15, true));
    }

    @Test
    void unknownNonPercentClassIsNotMultipliedByOneHundred() {
        assertFalse(OverlayAttrModFormat.isPercentageAttribute(String.class));
        assertFalse(OverlayAttrModFormat.isPercentageAttribute(Object.class));
        assertFalse(OverlayAttrModFormat.isPercentageAttribute(intattr.class));
        assertEquals("+8", OverlayAttrModFormat.formatModValue(8, OverlayAttrModFormat.isPercentageAttribute(String.class)));
    }

    @Test
    void knownPercentClassesStayPercent() {
        assertTrue(OverlayAttrModFormat.isPercentageAttribute(normattr.class));
        assertTrue(OverlayAttrModFormat.isPercentageAttribute(pmattr.class));
        assertTrue(OverlayAttrModFormat.isPercentageAttribute(haven.res.ui.tt.attrmod.inormattr.class));
    }

    @Test
    void gildingChanceStillScalesZeroToOneFractionsByOneHundred() throws Exception {
        assertEquals(20, (int) Math.round(100 * 0.20));
        assertEquals(30, (int) Math.round(100 * 0.30));

        String src = Files.readString(Path.of("src/nurgling/NTooltip.java"));
        int methodAt = src.indexOf("private static LineResult renderGildingChanceLine");
        assertTrue(methodAt >= 0, "renderGildingChanceLine must remain in NTooltip");
        String method = src.substring(methodAt, src.indexOf("private static class GildingStatData", methodAt));
        assertTrue(method.contains("100 * pmin"), "gilding chance must still use 100 * pmin");
        assertTrue(method.contains("100 * pmax"), "gilding chance must still use 100 * pmax");
        assertFalse(method.contains("OverlayAttrModFormat"),
                "gilding chance formatter must stay independent of AttrMod overlay percent");
    }

    @Test
    void overlayPercentPrintersUseSharedHelperNotInlineTimesOneHundred() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/NTooltip.java"));
        assertTrue(src.contains("OverlayAttrModFormat.formatModValue"),
                "NTooltip overlay stats must format via OverlayAttrModFormat");
        assertTrue(src.contains("OverlayAttrModFormat.isPercentageAttribute"),
                "NTooltip overlay stats must classify percent attrs via OverlayAttrModFormat");
        assertFalse(src.contains("double percent = modValue * 100"),
                "NTooltip must not inline AttrMod percent * 100");
        assertFalse(src.contains("Default to percentage if unknown"),
                "unknown AttrMod classes must not default to percent");
    }

    @Test
    void decimalStyleKeepsWholeAndOneFractionDigits() {
        assertEquals("+15.5%", OverlayAttrModFormat.formatModValue(0.155, true));
        assertEquals("+5.5%", OverlayAttrModFormat.formatModValue(5.5, true));
    }
}
