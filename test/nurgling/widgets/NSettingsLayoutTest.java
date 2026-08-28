package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NSettingsLayoutTest {
    @Test
    void pinsButtonsInsideShortSettingsWindow() {
        NSettingsLayout layout = NSettingsLayout.calculate(
                new Coord(800, 538), 210, 26,
                new Coord(100, 30), true, 10);

        assertEquals(498, layout.saveButton.y);
        assertEquals(498, layout.cancelButton.y);
        assertEquals(498, layout.backButton.y);
        assertTrue(layout.saveButton.y + 30 <= 538);
    }

    @Test
    void givesSidebarAndPanelOnlyTheSpaceAboveFooter() {
        NSettingsLayout layout = NSettingsLayout.calculate(
                new Coord(800, 538), 210, 26,
                new Coord(100, 30), true, 10);

        assertEquals(new Coord(200, 478), layout.sidebarSize);
        assertEquals(new Coord(580, 462), layout.panelSize);
    }

    @Test
    void laysButtonsFromRightToLeftAndOmitsBackWhenUnavailable() {
        NSettingsLayout layout = NSettingsLayout.calculate(
                new Coord(620, 400), 210, 26,
                new Coord(100, 30), false, 10);

        assertEquals(new Coord(510, 360), layout.saveButton);
        assertEquals(new Coord(400, 360), layout.cancelButton);
        assertEquals(null, layout.backButton);
    }
}
