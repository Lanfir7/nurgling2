package nurgling.widgets.nsettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HotkeyTabLayoutTest {
    @Test void narrowTabStripKeepsSelectedTabReachable() {
        HotkeyTabLayout layout = HotkeyTabLayout.calculate(widths(70, 80, 90, 100), 180, 3, 6);
        assertTrue(layout.rect(3).x >= 0);
        assertTrue(layout.rect(3).right() <= 180);
        assertTrue(layout.canScrollLeft());
    }

    @Test void visibleRangeAndRightScrollBoundaryAreReported() {
        HotkeyTabLayout layout = HotkeyTabLayout.calculate(widths(70, 80, 90, 100), 180, 0, 6);
        assertEquals(0, layout.visibleRange().start);
        assertEquals(3, layout.visibleRange().end);
        assertFalse(layout.canScrollLeft());
        assertTrue(layout.canScrollRight());

        HotkeyTabLayout exact = HotkeyTabLayout.calculate(widths(70, 80, 90, 100), 358, 3, 6);
        assertFalse(exact.canScrollLeft());
        assertFalse(exact.canScrollRight());
    }

    private static int[] widths(int... values) { return values; }
}
