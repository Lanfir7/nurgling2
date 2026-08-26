package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NLoginAccountLookTest {
    @Test
    void cardIsNarrowerThanSessionTabWithPortrait() {
        assertTrue(NLoginAccountLook.CARD_WIDTH < haven.UI.scale(180));
    }

    @Test
    void cardIsShorterWithoutAuthSubtitle() {
        assertTrue(NLoginAccountLook.CARD_HEIGHT < haven.UI.scale(40));
    }

    @Test
    void hoverUsesSessionGreen() {
        Color c = NLoginAccountLook.accent(true);
        assertEquals(0x99, c.getRed());
        assertEquals(0xFF, c.getGreen());
        assertEquals(0x84, c.getBlue());
    }

    @Test
    void idleUsesSessionBronze() {
        Color c = NLoginAccountLook.accent(false);
        assertEquals(0x91, c.getRed());
        assertEquals(0x60, c.getGreen());
        assertEquals(0x2E, c.getBlue());
    }
}
