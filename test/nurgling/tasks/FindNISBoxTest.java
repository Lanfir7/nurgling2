package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindNISBoxTest {
    @Test
    void trackedGobMustStayMissingBeforeWaitFinishes() {
        assertFalse(FindNISBox.targetGone(true, false, 1));
        assertFalse(FindNISBox.targetGone(true, false, 149));
        assertTrue(FindNISBox.targetGone(true, false, 150));
        assertFalse(FindNISBox.targetGone(true, true, 150));
    }
}
