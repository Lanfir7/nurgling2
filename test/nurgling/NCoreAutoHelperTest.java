package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCoreAutoHelperTest {
    @Test
    void autoHelperDoesNotStartBeforeGameUiExists() {
        assertFalse(NCore.shouldStartAutoHelper(false, true, false, false));
    }

    @Test
    void autoHelperStartsOnlyWhenEnabledIdleAndAlive() {
        assertTrue(NCore.shouldStartAutoHelper(true, true, false, false));
        assertFalse(NCore.shouldStartAutoHelper(true, false, false, false));
        assertFalse(NCore.shouldStartAutoHelper(true, true, true, false));
        assertFalse(NCore.shouldStartAutoHelper(true, true, false, true));
    }
}
