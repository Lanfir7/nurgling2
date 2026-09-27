package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwimmingAutoOffTimerTest {
    @Test
    void requestsOffOnceFiveMinutesAfterSwimmingTurnsOn() {
        SwimmingAutoOffTimer timer = new SwimmingAutoOffTimer();
        long start = 1_000_000L;
        timer.onState(true, start);
        timer.onState(true, start + SwimmingAutoOffTimer.TIMEOUT_NANOS / 2);

        assertFalse(timer.isDue(true, start + SwimmingAutoOffTimer.TIMEOUT_NANOS - 1));
        assertTrue(timer.isDue(true, start + SwimmingAutoOffTimer.TIMEOUT_NANOS));
        timer.requested();
        assertFalse(timer.isDue(true, start + SwimmingAutoOffTimer.TIMEOUT_NANOS + 1));
    }

    @Test
    void manualOffAndOnStartsFreshTimerAndSettingCanDisableIt() {
        SwimmingAutoOffTimer timer = new SwimmingAutoOffTimer();
        timer.onState(true, 10);
        assertFalse(timer.isDue(false, 10 + SwimmingAutoOffTimer.TIMEOUT_NANOS));
        timer.onState(false, 10 + SwimmingAutoOffTimer.TIMEOUT_NANOS);
        timer.onState(true, 20 + SwimmingAutoOffTimer.TIMEOUT_NANOS);

        assertFalse(timer.isDue(true, 20 + SwimmingAutoOffTimer.TIMEOUT_NANOS));
        assertTrue(timer.isDue(true, 20 + 2 * SwimmingAutoOffTimer.TIMEOUT_NANOS));
    }
}
