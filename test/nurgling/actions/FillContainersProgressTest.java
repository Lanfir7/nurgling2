package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillContainersProgressTest {
    @Test
    void unchangedSourceAndUnreadyContainerStopsTheLoop() {
        assertFalse(FillContainers.shouldContinue(Results.SUCCESS(), 3, 3, false));
    }

    @Test
    void rejectedTransferStopsEvenIfTheSourceCountChanged() {
        assertFalse(FillContainers.shouldContinue(Results.FAIL(), 3, 2, false));
    }

    @Test
    void confirmedUnitTransferCanContinueFilling() {
        assertTrue(FillContainers.shouldContinue(Results.SUCCESS(), 3, 2, false));
    }

    @Test
    void ReadyContainerNeedsNoFurtherProgress() {
        assertTrue(FillContainers.shouldContinue(Results.SUCCESS(), 3, 3, true));
    }
}
