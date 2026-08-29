package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NWindowTitleLayoutTest {
    @Test
    void searchSitsBeforeCloseButton() {
        NWindowTitleLayout layout = NWindowTitleLayout.calculate(
                860, 50, 835, 250, 140, 6, 21, 18);

        assertTrue(layout.visible);
        assertEquals(250, layout.width);
        assertTrue(layout.position.x + layout.width <= 829);
    }

    @Test
    void searchHidesWhenTitleBarIsTooNarrow() {
        NWindowTitleLayout layout = NWindowTitleLayout.calculate(
                240, 90, 215, 250, 140, 6, 21, 18);

        assertFalse(layout.visible);
    }

    @Test
    void titleWidgetNeverOverlapsTitleOrCloseButton() {
        NWindowTitleLayout layout = NWindowTitleLayout.calculate(
                500, 120, 475, 250, 140, 6, 21, 18);

        assertTrue(layout.position.x >= 132);
        assertTrue(layout.position.x + layout.width <= 469);
    }

    @Test
    void titleBarMakesRoomAboveAndBelowEmbeddedControl() {
        assertEquals(26, NWindowTitleLayout.requiredTitleHeight(21, 20, 3));
        assertEquals(21, NWindowTitleLayout.requiredTitleHeight(21, 12, 3));
    }
}
