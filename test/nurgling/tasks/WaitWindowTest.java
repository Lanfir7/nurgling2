package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class WaitWindowTest {
    @Test
    void softTimeoutDoesNotHangOrKillTheBot() {
        WaitWindow wait = WaitWindow.withSoftTimeout("Stone Column", 200);
        assertFalse(wait.infinite);
        assertFalse(wait.criticalOnTimeout);
    }
}
