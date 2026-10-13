package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmokeringBotSafetyTest {
    @Test
    void fullInventoryDoesNotMasqueradeAsEmptyBarrels() {
        assertEquals("No inventory room for Pipestuff", SmokeringBot.restockBlockReason(0, 0));
        assertNull(SmokeringBot.restockBlockReason(0, 1));
        assertNull(SmokeringBot.restockBlockReason(1, 0));
        assertEquals(SmokeringBot.RestockOutcome.BLOCKED,
                SmokeringBot.restockOutcome("No inventory room for Pipestuff", 0));
        assertEquals(SmokeringBot.RestockOutcome.EMPTY, SmokeringBot.restockOutcome(null, 0));
        assertEquals(SmokeringBot.RestockOutcome.READY, SmokeringBot.restockOutcome(null, 1));
        assertNotNull(SmokeringBot.barrelTripFailure(false, false, 0));
        assertNotNull(SmokeringBot.barrelTripFailure(true, true, 0));
        assertNull(SmokeringBot.barrelTripFailure(true, false, 0));
        assertNull(SmokeringBot.barrelTripFailure(true, true, 1));
    }

    @Test
    void fullInventoryLeavesUnequippedPipeInHand() {
        assertEquals(SmokeringBot.PipeStopPlacement.HAND, SmokeringBot.stopPlacement(false, false));
        assertEquals(SmokeringBot.PipeStopPlacement.INVENTORY, SmokeringBot.stopPlacement(false, true));
        assertEquals(SmokeringBot.PipeStopPlacement.STILL_EQUIPPED, SmokeringBot.stopPlacement(true, false));
    }
}
