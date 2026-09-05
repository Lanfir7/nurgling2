package nurgling.actions.bots.silk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferSilkwormsFromHTablesToFeedingTest {
    @Test
    void visitsHerbalistTablesEvenWhenNoWormsCanBeTransferred() {
        assertTrue(TransferSilkwormsFromHTablesToFeeding.shouldVisitHerbalistTables(0));
    }
}
