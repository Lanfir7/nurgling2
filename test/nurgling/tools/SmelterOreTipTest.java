package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmelterOreTipTest {

    @Test
    void regularUnlitIsFiftyFiveMinutes() {
        assertEquals(3300, SmelterOreTip.REGULAR_SECONDS);
        assertEquals(3300, SmelterOreTip.remainingSeconds(0, false));
        assertEquals("55 min", KilnFiringTip.formatRemaining(
                SmelterOreTip.remainingSeconds(0, false), "{0} min", "{0} h {1} min", "{0} h"));
    }

    @Test
    void wellMinedUnlitIsNineTwelfthsOfFiftyFiveMinutes() {
        assertEquals(2475, SmelterOreTip.WELL_MINED_SECONDS);
        assertEquals(2475, SmelterOreTip.remainingSeconds(0, true));
        assertEquals(9 * 3300 / 12, SmelterOreTip.remainingSeconds(0, true));
        assertEquals("41 min", KilnFiringTip.formatRemaining(
                SmelterOreTip.remainingSeconds(0, true), "{0} min", "{0} h {1} min", "{0} h"));
    }

    @Test
    void regularFourteenPercentLeavesAboutFortySevenMinutes() {
        int percent = KilnFiringTip.resolvedPercent(0, 0.14);
        assertEquals(14, percent);
        int left = SmelterOreTip.remainingSeconds(percent, false);
        assertEquals((int) Math.round(0.86 * 3300), left);
        assertEquals("47 min", KilnFiringTip.formatRemaining(left, "{0} min", "{0} h {1} min", "{0} h"));
    }

    @Test
    void wellMinedFourteenPercentLeavesAboutThirtyFiveMinutes() {
        int percent = KilnFiringTip.resolvedPercent(0, 0.14);
        assertEquals(14, percent);
        int left = SmelterOreTip.remainingSeconds(percent, true);
        assertEquals((int) Math.round(0.86 * 2475), left);
        assertEquals("35 min", KilnFiringTip.formatRemaining(left, "{0} min", "{0} h {1} min", "{0} h"));
    }

    @Test
    void remainingClipsMeterLikeKiln() {
        assertEquals(0, SmelterOreTip.remainingSeconds(100, false));
        assertEquals(0, SmelterOreTip.remainingSeconds(140, true));
        assertEquals(3300, SmelterOreTip.remainingSeconds(-5, false));
    }

    @Test
    void windowCapGatesOreAndSmithSmelterOnly() {
        assertTrue(SmelterOreTip.isSmelterWindow("Ore Smelter"));
        assertTrue(SmelterOreTip.isSmelterWindow("Smith's Smelter"));
        assertFalse(SmelterOreTip.isSmelterWindow("Kiln"));
        assertFalse(SmelterOreTip.isSmelterWindow("Oven"));
        assertFalse(SmelterOreTip.isSmelterWindow("Inventory"));
        assertFalse(SmelterOreTip.isSmelterWindow("primsmelter"));
        assertFalse(SmelterOreTip.isSmelterWindow("Stack Furnace"));
        assertFalse(SmelterOreTip.isSmelterWindow("Tar Kiln"));
        assertFalse(SmelterOreTip.isSmelterWindow(null));
        assertFalse(SmelterOreTip.isSmelterWindow(""));
    }

    @Test
    void shouldRenderKnownOreInSmelterEvenUnlit() {
        assertTrue(SmelterOreTip.shouldRender("Ore Smelter", "Cassiterite", false, false));
        assertTrue(SmelterOreTip.shouldRender("Smith's Smelter", "Iron Ochre", false, false));
        assertTrue(SmelterOreTip.shouldRender("Ore Smelter", "Peacock Ore", false, false));
        assertTrue(SmelterOreTip.shouldRender("Ore Smelter", "Dross", false, false));
    }

    @Test
    void shouldRenderWellMinedOrMeteredUnknownButNeverFuel() {
        assertTrue(SmelterOreTip.shouldRender("Ore Smelter", "Mystery Ore", true, false));
        assertTrue(SmelterOreTip.shouldRender("Ore Smelter", "Mystery Ore", false, true));
        assertFalse(SmelterOreTip.shouldRender("Ore Smelter", "Mystery Goo", false, false));
        assertFalse(SmelterOreTip.shouldRender("Ore Smelter", "Coal", false, false));
        assertFalse(SmelterOreTip.shouldRender("Ore Smelter", "Coal", false, true));
        assertFalse(SmelterOreTip.shouldRender("Ore Smelter", "Charcoal", true, true));
        assertFalse(SmelterOreTip.shouldRender("Ore Smelter", "Black Coal", false, true));
    }

    @Test
    void shouldRenderSkippedOutsideOreSmithWindows() {
        assertFalse(SmelterOreTip.shouldRender("Kiln", "Cassiterite", false, true));
        assertFalse(SmelterOreTip.shouldRender("Oven", "Cassiterite", true, true));
        assertFalse(SmelterOreTip.shouldRender("Inventory", "Cassiterite", false, true));
        assertFalse(SmelterOreTip.shouldRender("Stack Furnace", "Cassiterite", false, true));
        assertFalse(SmelterOreTip.shouldRender("primsmelter", "Cassiterite", false, true));
        assertFalse(SmelterOreTip.shouldRender(null, "Cassiterite", false, true));
    }

    @Test
    void kilnTooltipStillIgnoresSmelterWindows() {
        assertNull(KilnFiringTip.shouldRender("Ore Smelter", "Coade Clay"));
        assertNull(KilnFiringTip.shouldRender("Smith's Smelter", "Coade Clay"));
    }

    /**
     * Every NGItem gets both NKilnInfo and NSmelterInfo. Returning kiln's
     * needUpdate() immediately would skip the smelter tip whenever the window
     * is not a kiln (needUpdate is false), so the smelter bar would never
     * refresh as the meter changes.
     */
    @Test
    void needlongtipDoesNotReturnKilnNeedUpdateBeforeCheckingSmelter() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Paths.get("src/nurgling/NGItem.java"));
        int kiln = src.indexOf("inf instanceof NKilnInfo");
        int smelter = src.indexOf("inf instanceof NSmelterInfo");
        assertTrue(kiln >= 0 && smelter > kiln, src);
        String kilnBlock = src.substring(kiln, smelter);
        assertFalse(kilnBlock.contains("return ((NKilnInfo)"),
                "unconditional kiln return skips NSmelterInfo: " + kilnBlock);
        assertTrue(kilnBlock.contains("needUpdate()"), kilnBlock);
    }
}
