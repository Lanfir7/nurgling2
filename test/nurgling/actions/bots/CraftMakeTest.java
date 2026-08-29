package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftMakeTest {

    @Test
    void missingWindowIsClosed() {
        assertFalse(CraftMake.windowOpen(null));
    }

    @Test
    void stopWhenWindowClosedOrMakeFailed() {
        assertTrue(CraftMake.shouldStop(true, false));
        assertTrue(CraftMake.shouldStop(false, true));
        assertFalse(CraftMake.shouldStop(false, false));
    }

    @Test
    void noProgWithoutErrorIsInstantSuccess() {
        assertTrue(CraftMake.instantSuccess(false, false, false));
        assertFalse(CraftMake.instantSuccess(false, false, true));
        assertFalse(CraftMake.instantSuccess(false, true, false));
        assertFalse(CraftMake.instantSuccess(true, false, false));
    }
}
