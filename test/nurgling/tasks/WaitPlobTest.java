package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitPlobTest {
    @Test
    void backgroundSessionCanProceedWithoutGlGhost() {
        assertTrue(WaitPlob.isSatisfied(true, true, false, false));
    }

    @Test
    void visualPlacementStillWaitsForReadyGhost() {
        assertFalse(WaitPlob.isSatisfied(true, true, false, true));
        assertTrue(WaitPlob.isSatisfied(true, true, true, true));
    }

    @Test
    void timeoutWaitDoesNotRequireGhostForever() {
        WaitPlob wait = WaitPlob.withTimeout(false, 10);
        assertFalse(wait.infinite);
        assertEquals(10, wait.maxCounter);
    }

    @Test
    void softTimeoutDoesNotKillTheBot() {
        WaitPlob wait = WaitPlob.withSoftTimeout(false, 10);
        assertFalse(wait.infinite);
        assertFalse(wait.criticalOnTimeout);
    }

    @Test
    void softTimeoutCanBindASessionGui() {
        WaitPlob wait = WaitPlob.withSoftTimeout(false, 10, null);
        assertFalse(wait.infinite);
        assertFalse(wait.criticalOnTimeout);
        assertEquals(10, wait.maxCounter);
    }

    @Test
    void missingPlaceMessageIsNotReady() {
        assertFalse(WaitPlob.isSatisfied(true, false, false, false));
        assertFalse(WaitPlob.isSatisfied(false, true, true, false));
    }
}
