package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsPoseMovTest {
    @Test
    void reportsFailureWhenMovementNeverStartsBeforeTimeout() {
        IsPoseMov task = new IsPoseMov(null, null, null);

        for (int i = 0; i < 200; i++) {
            assertFalse(task.check());
        }
        assertTrue(task.check());
        assertFalse(task.getResult());
    }
}
