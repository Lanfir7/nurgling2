package nurgling.sessions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nurgling.sessions.SleepLogout.Action;

class SleepLogoutTest {
    @Test
    void sleepPetalIsRecognized() {
        assertTrue(SleepLogout.isSleepAction("Sleep"));
        assertTrue(SleepLogout.isSleepAction("sleep"));
        assertFalse(SleepLogout.isSleepAction("Chop"));
        assertFalse(SleepLogout.isSleepAction(null));
        assertFalse(SleepLogout.isSleepAction(""));
    }

    @Test
    void firstLoginCharlistDoesNotExit() {
        assertEquals(Action.NONE, SleepLogout.decide(false, 1));
    }

    @Test
    void sleepWithOnlySessionClosesSession() {
        assertEquals(Action.CLOSE_SESSION, SleepLogout.decide(true, 1));
        assertEquals(Action.CLOSE_SESSION, SleepLogout.decide(true, 0));
    }

    @Test
    void sleepWithOtherSessionsOnlyClosesThisOne() {
        assertEquals(Action.CLOSE_SESSION, SleepLogout.decide(true, 2));
    }
}
