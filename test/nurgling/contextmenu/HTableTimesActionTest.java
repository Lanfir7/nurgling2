package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HTableTimesActionTest {

    @Test
    void isUiOnlyAndDoesNotCreateABot() {
        HTableTimesAction action = new HTableTimesAction();
        assertTrue(action.isUiAction());
        assertNull(action.create(null));
    }
}
