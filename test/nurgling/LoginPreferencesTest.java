package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginPreferencesTest {
    @Test
    void openInventoryOnlyWhenBooleanTrue() {
        assertTrue(LoginPreferences.shouldOpenInventory(Boolean.TRUE));
        assertFalse(LoginPreferences.shouldOpenInventory(Boolean.FALSE));
        assertFalse(LoginPreferences.shouldOpenInventory(null));
        assertFalse(LoginPreferences.shouldOpenInventory("true"));
    }

    @Test
    void speedWaitsUntilMaxAllowsPreferred() {
        assertNull(LoginPreferences.speedToApply(0, 0, 2));
        assertEquals(2, LoginPreferences.speedToApply(0, 3, 2));
        assertEquals(1, LoginPreferences.speedToApply(0, 1, 2));
        assertNull(LoginPreferences.speedToApply(2, 3, 2));
        assertNull(LoginPreferences.speedToApply(0, 3, null));
        assertNull(LoginPreferences.speedToApply(0, 3, 9));
    }
}
