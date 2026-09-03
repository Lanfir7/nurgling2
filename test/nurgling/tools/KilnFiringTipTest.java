package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KilnFiringTipTest {

    @Test
    void kilnCaptionOnly() {
        assertTrue(KilnFiringTip.isKilnWindow("Kiln"));
        assertFalse(KilnFiringTip.isKilnWindow("Oven"));
        assertFalse(KilnFiringTip.isKilnWindow("Ore Smelter"));
        assertFalse(KilnFiringTip.isKilnWindow("Tar Kiln"));
        assertFalse(KilnFiringTip.isKilnWindow("Inventory"));
        assertFalse(KilnFiringTip.isKilnWindow(null));
        assertFalse(KilnFiringTip.isKilnWindow(""));
    }

    @Test
    void meterPercentTreatsMissingMeterAsZeroAndClips() {
        assertEquals(0, KilnFiringTip.meterPercent(0));
        assertEquals(76, KilnFiringTip.meterPercent(76));
        assertEquals(100, KilnFiringTip.meterPercent(100));
        assertEquals(0, KilnFiringTip.meterPercent(-5));
        assertEquals(100, KilnFiringTip.meterPercent(140));
    }

    @Test
    void meterChangedWhenPercentDiffers() {
        assertTrue(KilnFiringTip.meterChanged(Integer.MIN_VALUE, 0));
        assertTrue(KilnFiringTip.meterChanged(76, 77));
        assertFalse(KilnFiringTip.meterChanged(76, 76));
        assertFalse(KilnFiringTip.meterChanged(0, 0));
    }

    @Test
    void formatRemainingUsesMinutesUnderAnHourAndHoursAfter() {
        int coadeLeft = KilnFuelCatalog.remainingSeconds("Coade Clay", 76).getAsInt();
        assertEquals("26 min", KilnFiringTip.formatRemaining(coadeLeft, "{0} min", "{0} h {1} min", "{0} h"));

        int coadeFull = KilnFuelCatalog.remainingSeconds("Coade Clay", 0).getAsInt();
        assertEquals("1 h 49 min", KilnFiringTip.formatRemaining(coadeFull, "{0} min", "{0} h {1} min", "{0} h"));

        assertEquals("9 min", KilnFiringTip.formatRemaining(
                KilnFuelCatalog.parseRealTimeSeconds("0:08:58"), "{0} min", "{0} h {1} min", "{0} h"));
        assertEquals("2 h", KilnFiringTip.formatRemaining(7200, "{0} min", "{0} h {1} min", "{0} h"));
        assertEquals("0 min", KilnFiringTip.formatRemaining(0, "{0} min", "{0} h {1} min", "{0} h"));
    }

    @Test
    void extraTipSkippedWhenUnknownOrNotKiln() {
        assertNull(KilnFiringTip.shouldRender("Kiln", "Mystery Goo"));
        assertNull(KilnFiringTip.shouldRender("Oven", "Coade Clay"));
        assertNull(KilnFiringTip.shouldRender(null, "Coade Clay"));
        assertEquals("Coade Clay", KilnFiringTip.shouldRender("Kiln", "Coade Clay"));
        assertEquals("Unfired Garden Pot", KilnFiringTip.shouldRender("Kiln", "Unfired Garden Pot"));
    }

    @Test
    void progressBarFillMatchesMeterPercent() {
        Color fill = new Color(255, 196, 92);
        Color bg = new Color(20, 24, 22);
        BufferedImage bar = KilnFiringTip.progressBar(100, 8, 76, fill, bg, Color.YELLOW);
        assertEquals(100, bar.getWidth());
        assertEquals(8, bar.getHeight());
        assertEquals(76, KilnFiringTip.fillWidth(100, 76));
        assertEquals(fill.getRGB(), bar.getRGB(1, 4));
        assertEquals(fill.getRGB(), bar.getRGB(75, 4));
        assertEquals(bg.getRGB(), bar.getRGB(77, 4));
        assertEquals(bg.getRGB(), bar.getRGB(98, 4));
        assertEquals(Color.YELLOW.getRGB(), bar.getRGB(0, 0));
    }

    @Test
    void composeBarAndLabelStacksTimeUnderBar() {
        BufferedImage bar = new BufferedImage(40, 6, BufferedImage.TYPE_INT_ARGB);
        BufferedImage label = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        BufferedImage stacked = KilnFiringTip.composeBarAndLabel(bar, label, 3);
        assertEquals(40, stacked.getWidth());
        assertEquals(6 + 3 + 10, stacked.getHeight());
    }
}
