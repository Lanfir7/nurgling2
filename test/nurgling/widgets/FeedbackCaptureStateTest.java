package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedbackCaptureStateTest {
    @Test
    void selectionCancellationAndFailureRestoreWindowsOnlyOnce() {
        AtomicInteger restores = new AtomicInteger();
        FeedbackCaptureController.VisibilityState state =
                new FeedbackCaptureController.VisibilityState(restores::incrementAndGet);

        state.restore();
        state.restore();

        assertEquals(1, restores.get());
    }
}
