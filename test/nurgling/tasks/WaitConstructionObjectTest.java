package nurgling.tasks;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WaitConstructionObjectTest {
    @Test
    void softTimeoutDoesNotKillTheBot() {
        WaitConstructionObject wait = WaitConstructionObject.withSoftTimeout(Coord2d.z, 200);
        assertFalse(wait.infinite);
        assertFalse(wait.criticalOnTimeout);
        assertEquals(200, wait.maxCounter);
    }
}
