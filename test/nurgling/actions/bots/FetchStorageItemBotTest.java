package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FetchStorageItemBotTest {
    @Test
    void exactOrGreaterCountCompletesRequest() {
        assertTrue(FetchStorageItemBot.isComplete(4, 4));
        assertTrue(FetchStorageItemBot.isComplete(4, 5));
    }

    @Test
    void partialCountDoesNotCompleteRequest() {
        assertFalse(FetchStorageItemBot.isComplete(4, 3));
        assertFalse(FetchStorageItemBot.isComplete(4, 0));
    }
}
