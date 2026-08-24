package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectFromGobTest {
    @Test
    void stopAtIsInventoryTotalNotExtraPicks() {
        assertFalse(CollectFromGob.reachedStopAt(0, 4));
        assertFalse(CollectFromGob.reachedStopAt(3, 4));
        assertTrue(CollectFromGob.reachedStopAt(4, 4));
        assertTrue(CollectFromGob.reachedStopAt(5, 4));
        assertFalse(CollectFromGob.reachedStopAt(10, 0));
    }

    @Test
    void cancelHarvestWhenEnoughWhileStillPicking() {
        assertTrue(CollectFromGob.shouldCancelHarvest(true, true));
        assertFalse(CollectFromGob.shouldCancelHarvest(true, false));
        assertFalse(CollectFromGob.shouldCancelHarvest(false, true));
    }

    @Test
    void collectStartWaitDoesNotHangOnMount() {
        assertFalse(CollectFromGob.collectStartWaitDone(false, false, false, 0, 200));
        assertFalse(CollectFromGob.collectStartWaitDone(false, false, false, 199, 200));
        assertTrue(CollectFromGob.collectStartWaitDone(true, false, false, 0, 200));
        assertTrue(CollectFromGob.collectStartWaitDone(false, true, false, 0, 200));
        assertTrue(CollectFromGob.collectStartWaitDone(false, false, true, 0, 200));
        assertTrue(CollectFromGob.collectStartWaitDone(false, false, false, 200, 200));
    }

    @Test
    void harvestCancelDoesNotWaitForeverForIdle() {
        assertTrue(CollectFromGob.harvestCancelWaitDone(true, false, 0, 200));
        assertTrue(CollectFromGob.harvestCancelWaitDone(false, false, 0, 200));
        assertTrue(CollectFromGob.harvestCancelWaitDone(false, true, 200, 200));
        assertFalse(CollectFromGob.harvestCancelWaitDone(false, true, 0, 200));
        assertFalse(CollectFromGob.harvestCancelWaitDone(false, true, 199, 200));
    }
}
