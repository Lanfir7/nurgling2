package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarterWindowLayoutTest {
    @Test
    void viewportFitsTheAvailableScreenSpace() {
        assertEquals(360, BarterWindowLayout.viewportHeight(1800, 360));
        assertEquals(180, BarterWindowLayout.viewportHeight(180, 360));
        assertEquals(1, BarterWindowLayout.viewportHeight(180, 0));
    }

    @Test
    void scrollAndRowPositionAreClampedAndTranslatedTogether() {
        assertEquals(0, BarterWindowLayout.scrollOffset(-50, 1200, 400));
        assertEquals(800, BarterWindowLayout.scrollOffset(900, 1200, 400));
        assertEquals(280, BarterWindowLayout.rowY(40, 1040, 800));
    }

    @Test
    void rowsSortFromTopToBottomThenLeftToRight() {
        assertEquals(-1, BarterWindowLayout.compareOrigins(100, 0, 200, 0));
        assertEquals(1, BarterWindowLayout.compareOrigins(200, 0, 100, 0));
        assertEquals(-1, BarterWindowLayout.compareOrigins(100, 10, 100, 20));
    }
}
