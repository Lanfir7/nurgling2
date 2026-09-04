package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitItemFromPileTest {
    @Test
    void partialTransferFinishesAfterProgressStalls() {
        WaitItemFromPile.TransferWaitBudget wait =
                new WaitItemFromPile.TransferWaitBudget(4, 3);

        assertFalse(wait.tick(0));
        assertFalse(wait.tick(2));
        assertFalse(wait.tick(2));
        assertFalse(wait.tick(2));
        assertTrue(wait.tick(2));
    }

    @Test
    void reachingTargetFinishesImmediately() {
        WaitItemFromPile.TransferWaitBudget wait =
                new WaitItemFromPile.TransferWaitBudget(4, 250);

        assertTrue(wait.tick(4));
    }
}
