package nurgling.areas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaLabelRefreshSignalTest {
    @Test
    void requestIsConsumedOnce() {
        AreaLabelRefreshSignal signal = new AreaLabelRefreshSignal();

        assertFalse(signal.consume());
        signal.request();
        assertTrue(signal.consume());
        assertFalse(signal.consume());
    }

    @Test
    void repeatedRequestsCoalesce() {
        AreaLabelRefreshSignal signal = new AreaLabelRefreshSignal();

        signal.request();
        signal.request();

        assertTrue(signal.consume());
        assertFalse(signal.consume());
    }
}
