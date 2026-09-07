package nurgling.widgets.nsettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HotkeyTabLayoutTest {
    @Test void narrowTabStripKeepsSelectedTabReachable() {
        HotkeyTabLayout layout = HotkeyTabLayout.calculate(widths(70, 80, 90, 100), 180, 3, 6);
        assertTrue(layout.rect(3).x >= 0);
        assertTrue(layout.rect(3).right() <= 180);
        assertTrue(layout.canScrollLeft());
    }

    private static int[] widths(int... values) { return values; }
}
