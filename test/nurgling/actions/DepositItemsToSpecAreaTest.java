package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DepositItemsToSpecAreaTest {
    @Test
    void convertsFreeCellsToItemCapacityUsingStackDepth() {
        assertEquals(32, DepositItemsToSpecArea.itemsThatFit(8, 4));
        assertEquals(8, DepositItemsToSpecArea.itemsThatFit(8, 1));
    }
}
