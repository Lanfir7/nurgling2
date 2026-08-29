package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KilnFuelActionTest {

    @Test
    void isUiOnlyAndDoesNotCreateABot() {
        KilnFuelAction action = new KilnFuelAction();
        assertTrue(action.isUiAction());
        assertNull(action.create(null));
    }
}
