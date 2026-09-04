package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakeItemsFromPileTest {
    @Test
    void emptyTransferPassIsNotRetried() {
        assertFalse(TakeItemsFromPile.shouldRetryAfterPass(0));
        assertTrue(TakeItemsFromPile.shouldRetryAfterPass(1));
    }
}
