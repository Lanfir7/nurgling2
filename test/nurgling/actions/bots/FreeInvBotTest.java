package nurgling.actions.bots;

import nurgling.actions.Results;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FreeInvBotTest {

    @Test
    void failedFreeInventoryIsReturnedByBot() throws InterruptedException {
        Results result = FreeInvBot.runFreeInventory(
                gui -> Results.FAIL(), null);

        assertFalse(result.IsSuccess());
    }
}
