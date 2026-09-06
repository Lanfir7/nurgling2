package nurgling.tasks;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitForPlayerGridChangeTest {
    @Test
    void completesAsSoonAsThePlayersActualGridChanges() {
        AtomicLong currentGridId = new AtomicLong(101);
        AtomicLong clock = new AtomicLong(1_000);
        WaitForPlayerGridChange wait = new WaitForPlayerGridChange(
                101, currentGridId::get, clock::get, 10_000);

        assertFalse(wait.check());

        currentGridId.set(-1);
        assertFalse(wait.check());

        currentGridId.set(202);
        assertTrue(wait.check());
    }

    @Test
    void stopsWaitingAfterThePortalTimeoutWhenTheGridNeverChanges() {
        AtomicLong currentGridId = new AtomicLong(101);
        AtomicLong clock = new AtomicLong(1_000);
        WaitForPlayerGridChange wait = new WaitForPlayerGridChange(
                101, currentGridId::get, clock::get, 10_000);

        assertFalse(wait.check());

        clock.set(10_999);
        assertFalse(wait.check());

        clock.set(11_000);
        assertTrue(wait.check());
    }
}
