package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuckMasterPolicyTest {
    @Test
    void liveDuckAliasesRejectProcessedBirds() {
        assertTrue(DuckMaster.DRAKE.matches("Duck Drake"));
        assertFalse(DuckMaster.DRAKE.matches("Dead Duck Drake"));
        assertFalse(DuckMaster.DRAKE.matches("Plucked Duck Drake"));
        assertTrue(DuckMaster.HEN.matches("Duck Hen"));
        assertFalse(DuckMaster.HEN.matches("Dead Duck Hen"));
        assertFalse(DuckMaster.HEN.matches("Plucked Duck Hen"));
    }

    @Test
    void drakeReplacementRequiresAResidentWithLowerQuality() {
        assertTrue(DuckMaster.shouldReplaceDrake(10.0, 11.0));
        assertFalse(DuckMaster.shouldReplaceDrake(11.0, 11.0));
        assertFalse(DuckMaster.shouldReplaceDrake(-1.0, 11.0));
    }

    @Test
    void onlyEggsBelowTheBestCoopThresholdAreDiscarded() {
        assertTrue(DuckMaster.shouldDiscardEgg(9.99, 10.0));
        assertFalse(DuckMaster.shouldDiscardEgg(10.0, 10.0));
        assertFalse(DuckMaster.shouldDiscardEgg(10.01, 10.0));
    }

    @Test
    void dropOffStartsWhenAtMostTwoDuckSlotsRemain() {
        assertTrue(DuckMaster.needsDropOff(0));
        assertTrue(DuckMaster.needsDropOff(2));
        assertFalse(DuckMaster.needsDropOff(3));
    }
}
