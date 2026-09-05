package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepositItemsToSpecAreaTest {
    @Test
    void convertsFreeCellsToItemCapacityUsingStackDepth() {
        assertEquals(32, DepositItemsToSpecArea.itemsThatFit(8, 4));
        assertEquals(8, DepositItemsToSpecArea.itemsThatFit(8, 1));
    }

    @Test
    void refreshesSpaceOnlyWhileContainerInventoryIsOpen() {
        assertEquals(true, DepositItemsToSpecArea.shouldRefreshSpace(true, true));
        assertEquals(false, DepositItemsToSpecArea.shouldRefreshSpace(true, false));
        assertEquals(false, DepositItemsToSpecArea.shouldRefreshSpace(false, true));
    }
}
