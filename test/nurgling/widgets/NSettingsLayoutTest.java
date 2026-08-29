package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NSettingsLayoutTest {
    @Test
    void pinsButtonsInsideShortSettingsWindow() {
        NSettingsLayout layout = NSettingsLayout.calculate(
                new Coord(800, 538), 210,
                new Coord(100, 30), true, 10);

        assertEquals(498, layout.saveButton.y);
        assertEquals(498, layout.cancelButton.y);
        assertEquals(498, layout.backButton.y);
        assertTrue(layout.saveButton.y + 30 <= 538);
    }

    @Test
    void givesSidebarAndPanelOnlyTheSpaceAboveFooter() {
        NSettingsLayout layout = NSettingsLayout.calculate(
                new Coord(800, 538), 210,
                new Coord(100, 30), true, 10);

        assertEquals(new Coord(210, 478), layout.sidebarSize);
        assertEquals(new Coord(560, 478), layout.panelSize);
        assertEquals(new Coord(10, 10), layout.sidebarPosition);
        assertEquals(new Coord(230, 10), layout.panelPosition);
        assertEquals(new Coord(0, 488), layout.footerMaskPosition);
        assertEquals(new Coord(800, 50), layout.footerMaskSize);
    }

    @Test
    void laysButtonsFromRightToLeftAndOmitsBackWhenUnavailable() {
        NSettingsLayout layout = NSettingsLayout.calculate(
                new Coord(620, 400), 210,
                new Coord(100, 30), false, 10);

        assertEquals(new Coord(510, 360), layout.saveButton);
        assertEquals(new Coord(400, 360), layout.cancelButton);
        assertEquals(null, layout.backButton);
    }

    @Test
    void wideWindowUsesTwoColumns() {
        NSettingsLayout layout = NSettingsLayout.calculate(
                new Coord(800, 600), 210,
                new Coord(100, 24), true, 10);

        assertFalse(layout.compact);
        assertEquals(2, layout.columns);
        assertEquals(layout.footerTop,
                layout.panelPosition.y + layout.panelSize.y + 10);
    }

    @Test
    void narrowWindowUsesOneColumnAndKeepsButtonsInside() {
        NSettingsLayout layout = NSettingsLayout.calculate(
                new Coord(520, 360), 210,
                new Coord(100, 24), true, 10);

        assertTrue(layout.compact);
        assertEquals(1, layout.columns);
        assertTrue(layout.backButton.x >= 10);
        assertTrue(layout.saveButton.x + 100 <= 510);
    }
}
