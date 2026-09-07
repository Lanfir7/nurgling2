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
}
