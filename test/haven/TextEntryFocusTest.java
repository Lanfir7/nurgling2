package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextEntryFocusTest {
    @Test
    void openWindowDoesNotFocusAFieldUntilAsked() {
        Widget window = new Widget(Coord.of(100, 100));
        window.setfocusctl(true);
        window.hasfocus = true;

        Widget field = new Widget(Coord.of(40, 16));
        field.canfocus = true;
        field.autofocus = false;
        window.add(field);
        assertFalse(field.hasfocus);

        window.setfocus(field);
        assertTrue(field.hasfocus);
    }

    @Test
    void autofocusWidgetStillTakesTheFirstFocus() {
        Widget window = new Widget(Coord.of(100, 100));
        window.setfocusctl(true);
        window.hasfocus = true;

        Widget list = new Widget(Coord.of(40, 16));
        list.canfocus = true;
        list.autofocus = true;
        window.add(list);
        assertTrue(list.hasfocus);
    }
}
