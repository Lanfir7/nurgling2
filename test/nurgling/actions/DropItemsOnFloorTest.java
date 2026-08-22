package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropItemsOnFloorTest {
    @Test
    void keepsDroppingWhileTopLevelSlotsRemain() {
        assertTrue(DropItemsOnFloor.shouldKeepDropping(3));
        assertTrue(DropItemsOnFloor.shouldKeepDropping(1));
        assertFalse(DropItemsOnFloor.shouldKeepDropping(0));
    }

    @Test
    void dropSameMatchesCtrlAltLeftClick() {
        assertEquals("drop-same", DropItemsOnFloor.DROP_SAME);
        assertFalse(DropItemsOnFloor.DROP_SAME_ASCENDING);
    }
}
