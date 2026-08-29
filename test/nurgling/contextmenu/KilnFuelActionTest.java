package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KilnFuelActionTest {

    @Test
    void matchesKilnGobOnly() {
        assertTrue(KilnGobs.matches("gfx/terobjs/kiln"));
        assertFalse(KilnGobs.matches("gfx/terobjs/tarkiln"));
        assertFalse(KilnGobs.matches("gfx/terobjs/kiln/extra"));
        assertFalse(KilnGobs.matches(null));
        assertFalse(KilnGobs.matches(""));
    }

    @Test
    void isUiOnlyAndDoesNotCreateABot() {
        KilnFuelAction action = new KilnFuelAction();
        assertTrue(action.isUiAction());
        assertNull(action.create(null));
    }
}
