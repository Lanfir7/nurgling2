package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TanningRemainingTest {
    @Test
    void tubCaptionsAcceptedOthersRejected() {
        assertTrue(TanningRemaining.isTubWindow("Tub"));
        assertTrue(TanningRemaining.isTubWindow("Tanning Tub"));
        assertFalse(TanningRemaining.isTubWindow("Drying Frame"));
        assertFalse(TanningRemaining.isTubWindow(null));
        assertFalse(TanningRemaining.isTubWindow(""));
    }

    @Test
    void remainingUsesThirtyRealHours() {
        assertEquals(1800, TanningRemaining.remainingMinutes(0.0));
        assertEquals(900, TanningRemaining.remainingMinutes(0.50));
        assertEquals(108, TanningRemaining.remainingMinutes(0.94));
        assertEquals(0, TanningRemaining.remainingMinutes(1.0));
        assertEquals(0, TanningRemaining.remainingMinutes(1.2));
    }

    @Test
    void formatDropsZeroUnits() {
        assertEquals("15h", TanningRemaining.formatRemaining(900));
        assertEquals("1h48m", TanningRemaining.formatRemaining(108));
        assertEquals("45m", TanningRemaining.formatRemaining(45));
        assertEquals("", TanningRemaining.formatRemaining(0));
        assertEquals("", TanningRemaining.formatRemaining(-1));
    }

    @Test
    void overlayAddsTimeOnlyInTub() {
        assertEquals("50% 15h", TanningRemaining.overlayText(0.50, "Tub"));
        assertEquals("94% 1h48m", TanningRemaining.overlayText(0.94, "Tanning Tub"));
        assertEquals("100%", TanningRemaining.overlayText(1.0, "Tub"));
        assertEquals("94%", TanningRemaining.overlayText(0.94, "Drying Frame"));
        assertEquals("50%", TanningRemaining.overlayText(0.50, null));
    }
}
