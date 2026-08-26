package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapLoginScreenTest {
    @Test
    void nativeAndSteamBothUseSavedAccountList() {
        assertTrue(Bootstrap.usesNurglingLoginScreen("native"));
        assertTrue(Bootstrap.usesNurglingLoginScreen("steam"));
    }

    @Test
    void unknownAuthmechDoesNotUseSavedAccountList() {
        assertFalse(Bootstrap.usesNurglingLoginScreen("other"));
    }
}
