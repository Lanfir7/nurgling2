package nurgling.sessions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSwitchFromLoginTest {
    @Test
    void loginScreenKeepsTabBarOnDisplayedUi() {
        assertTrue(SessionSwitchFromLogin.keepTabBarOnDisplayedUi(null));
    }

    @Test
    void inGameSwitchMovesTabBarToTargetSession() {
        assertFalse(SessionSwitchFromLogin.keepTabBarOnDisplayedUi(new SessionContext()));
    }

    @Test
    void sesswitchMessageAbortsLoginWait() {
        assertTrue(SessionSwitchFromLogin.isSwitchMessage("sesswitch"));
        assertFalse(SessionSwitchFromLogin.isSwitchMessage("login"));
        assertFalse(SessionSwitchFromLogin.isSwitchMessage(null));
    }
}
