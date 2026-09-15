package nurgling.actions;

import nurgling.tasks.NTask;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoToAbortTest {
    @Test
    void abortStaysStickyAcrossPairedCompletionTasks() {
        AtomicBoolean aborted = new AtomicBoolean();
        AtomicBoolean firstCheck = new AtomicBoolean(true);
        AtomicInteger delegateCalls = new AtomicInteger();
        NTask delegate = new NTask() {
            @Override
            public boolean check() {
                delegateCalls.incrementAndGet();
                return false;
            }
        };

        NTask first = GoTo.abortable(delegate, () -> firstCheck.getAndSet(false), aborted);
        NTask second = GoTo.abortable(delegate, () -> false, aborted);

        assertTrue(first.check());
        assertTrue(second.check());
        assertTrue(aborted.get());
        assertEquals(0, delegateCalls.get());
    }
}
