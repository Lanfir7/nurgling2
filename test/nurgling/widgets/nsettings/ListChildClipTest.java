package nurgling.widgets.nsettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListChildClipTest {
    @Test
    void drawsRowFullyInsideList() {
        assertTrue(ListChildClip.shouldDraw(130, 0, 34));
        assertTrue(ListChildClip.shouldDraw(130, 96, 34));
    }

    @Test
    void skipsRowFullyAboveOrBelowList() {
        assertFalse(ListChildClip.shouldDraw(130, -34, 34));
        assertFalse(ListChildClip.shouldDraw(130, 130, 34));
        assertFalse(ListChildClip.shouldDraw(130, 136, 34));
    }

    @Test
    void drawsPartiallyOverflowingRow() {
        assertTrue(ListChildClip.shouldDraw(130, -10, 34));
        assertTrue(ListChildClip.shouldDraw(130, 120, 34));
    }

    @Test
    void skipsTextEntryThatSitsFullyOutsideVisibleStrip() {
        int visibleStrip = 4;
        int textEntryY = 8;
        int textEntryHeight = 20;
        assertFalse(ListChildClip.shouldDraw(visibleStrip, textEntryY, textEntryHeight));
    }

    @Test
    void drawsTextEntryThatIntersectsVisibleStrip() {
        assertTrue(ListChildClip.shouldDraw(20, 8, 20));
        assertTrue(ListChildClip.shouldDraw(30, -4, 20));
    }

    @Test
    void overlapsUsesClipWindowNotJustListHeight() {
        assertFalse(ListChildClip.overlaps(126, 130, 134, 20));
        assertTrue(ListChildClip.overlaps(126, 130, 120, 34));
        assertFalse(ListChildClip.overlaps(0, 50, 100, 34));
    }

    @Test
    void skipsEmptyViewportOrZeroHeight() {
        assertFalse(ListChildClip.shouldDraw(0, 0, 34));
        assertFalse(ListChildClip.shouldDraw(130, 10, 0));
        assertFalse(ListChildClip.overlaps(50, 50, 40, 20));
    }
}
