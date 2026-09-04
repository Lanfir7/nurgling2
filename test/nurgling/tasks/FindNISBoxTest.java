package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindNISBoxTest {
    @Test
    void missingTrackedGobFinishesOtherwiseInfiniteWait() {
        assertTrue(FindNISBox.targetGone(true, false));
        assertFalse(FindNISBox.targetGone(false, false));
        assertFalse(FindNISBox.targetGone(true, true));
    }
}
