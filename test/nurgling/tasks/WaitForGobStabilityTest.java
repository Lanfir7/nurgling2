package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitForGobStabilityTest {
    @Test
    void maxWaitExpiresEvenWhileGobCountKeepsChanging() {
        WaitForGobStability wait = new WaitForGobStability(600, 5_000);

        assertFalse(wait.checkAt(10, 1_000));
        assertFalse(wait.checkAt(11, 3_000));
        assertFalse(wait.checkAt(12, 5_999));
        assertTrue(wait.checkAt(13, 6_001));
    }
}
