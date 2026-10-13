package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightObjectProgressTimeoutTest {
    @Test
    void boundedWaitEndsEvenIfProgressNeverClears() {
        assertEquals(10000, LightObject.torchProgressTimeout(true));
        assertEquals(0, LightObject.torchProgressTimeout(false));
        assertFalse(LightObject.progressStartedOrTimedOut(false, 999, 1000));
        assertTrue(LightObject.progressStartedOrTimedOut(true, 999, 1000));
        assertTrue(LightObject.progressStartedOrTimedOut(false, 1000, 1000));
        assertFalse(LightObject.progressFinishedOrTimedOut(false, 999, 1000));
        assertTrue(LightObject.progressFinishedOrTimedOut(false, 1000, 1000));
        assertTrue(LightObject.progressFinishedOrTimedOut(true, 999, 1000));
    }
}
