package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WaitItemInHandTest {
    @Test
    void softTimeoutDoesNotHangOrKillTheBot() {
        WaitItemInHand wait = WaitItemInHand.withSoftTimeout(80);
        assertFalse(wait.infinite);
        assertFalse(wait.criticalOnTimeout);
        assertEquals(80, wait.maxCounter);
    }
}
