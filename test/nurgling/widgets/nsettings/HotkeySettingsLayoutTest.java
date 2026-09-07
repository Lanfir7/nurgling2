package nurgling.widgets.nsettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotkeySettingsLayoutTest {
    @Test
    void rowsStartBelowPinnedSearchTabsAndConflictFilter() {
        HotkeySettingsLayout layout = HotkeySettingsLayout.calculate(560, 478, 24, 28, 24, 30);
        assertTrue(layout.rows.y >= layout.filter.y + layout.filter.h);
        assertEquals(478, layout.viewport.h);
        assertTrue(layout.capture.x + layout.capture.w <= 560);
    }

    @Test
    void presetRowLeavesSearchTabsAndRowsNonOverlappingAtLargeFonts() {
        HotkeySettingsLayout layout = HotkeySettingsLayout.calculate(
                560, 530, 28, 32, 40, 30, 44);
        assertTrue(layout.presets.y + layout.presets.h <= layout.search.y);
        assertTrue(layout.search.y + layout.search.h <= layout.tabs.y);
        assertTrue(layout.tabs.y + layout.tabs.h <= layout.filter.y);
        assertTrue(layout.filter.y + layout.filter.h <= layout.rows.y);
    }

    @Test
    void selectedOverflowTabIsFullyReachable() {
        HotkeyTabLayout layout = HotkeyTabLayout.calculate(
                new int[] {72, 72, 72, 72, 72, 72, 72, 72, 72, 72, 72},
                480, 10, 2);
        HotkeyTabLayout.Rect selected = layout.rect(10);
        assertTrue(selected.x >= 0);
        assertTrue(selected.right() <= layout.viewportWidth());
        assertTrue(layout.visibleRange().contains(10));
    }
}
