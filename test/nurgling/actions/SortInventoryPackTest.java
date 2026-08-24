package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SortInventoryPackTest {
    @Test
    void tenItemsStackByThree() {
        assertEquals(Arrays.asList(3, 3, 3, 1), SortInventory.computePackedSlotSizes(10, 3));
    }

    @Test
    void exactFullStack() {
        assertEquals(Collections.singletonList(3), SortInventory.computePackedSlotSizes(3, 3));
    }

    @Test
    void remainderAfterOneFull() {
        assertEquals(Arrays.asList(3, 1), SortInventory.computePackedSlotSizes(4, 3));
    }

    @Test
    void zeroItems() {
        assertEquals(Collections.emptyList(), SortInventory.computePackedSlotSizes(0, 3));
    }

    @Test
    void maxSizeOneKeepsSingles() {
        assertEquals(Arrays.asList(1, 1, 1, 1, 1), SortInventory.computePackedSlotSizes(5, 1));
    }

    @Test
    void partialSmallerThanMax() {
        assertEquals(Collections.singletonList(2), SortInventory.computePackedSlotSizes(2, 5));
    }
}
