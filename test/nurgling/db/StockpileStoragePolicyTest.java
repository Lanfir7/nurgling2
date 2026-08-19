package nurgling.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockpileStoragePolicyTest {
    @Test
    void detectsStockpileResourceNames() {
        assertTrue(StockpileStoragePolicy.isStockpileRes("gfx/terobjs/stockpile-board"));
        assertTrue(StockpileStoragePolicy.isStockpileRes("gfx/terobjs/stockpile-pipeleaves"));
        assertFalse(StockpileStoragePolicy.isStockpileRes("gfx/terobjs/chest"));
        assertFalse(StockpileStoragePolicy.isStockpileRes(null));
    }

    @Test
    void disappearedItemsAreInventoryMinusRemainder() {
        List<StockpileStoragePolicy.Item> before = List.of(
                item("Stone", 12),
                item("Stone", 8),
                item("Stone", 12),
                item("Branch", 10)
        );
        List<StockpileStoragePolicy.Item> after = List.of(
                item("Stone", 8),
                item("Branch", 10)
        );

        List<StockpileStoragePolicy.Item> gone = StockpileStoragePolicy.disappeared(before, after);
        assertEquals(List.of(item("Stone", 12), item("Stone", 12)), gone);
    }

    @Test
    void appearedItemsAreNewInventoryEntries() {
        List<StockpileStoragePolicy.Item> before = List.of(item("Stone", 8));
        List<StockpileStoragePolicy.Item> after = List.of(item("Stone", 8), item("Stone", 12));

        List<StockpileStoragePolicy.Item> gained = StockpileStoragePolicy.appeared(before, after);
        assertEquals(List.of(item("Stone", 12)), gained);
    }

    @Test
    void fetchKeepsMatchingQualityAndRestocksTheRest() {
        List<StockpileStoragePolicy.Item> dumped = List.of(
                item("Stone", 5),
                item("Stone", 12),
                item("Stone", 9),
                item("Branch", 10)
        );

        StockpileStoragePolicy.FetchSplit split =
                StockpileStoragePolicy.splitForFetch(dumped, "Stone", 12, 12, 1);

        assertEquals(List.of(item("Stone", 12)), split.keep);
        assertEquals(List.of(item("Stone", 5), item("Stone", 9), item("Branch", 10)), split.restock);
    }

    @Test
    void stackContentsReplaceTheShell() {
        List<StockpileStoragePolicy.Item> expanded = StockpileStoragePolicy.expandSlot(
                "Lead Glance", 0, 10,
                List.of(item("Lead Glance", 20), item("Lead Glance", 19), item("Lead Glance", 18)));
        assertEquals(3, expanded.size());
        assertEquals(List.of(item("Lead Glance", 20), item("Lead Glance", 19), item("Lead Glance", 18)), expanded);
    }

    @Test
    void amountWithoutContentsCountsEachItem() {
        List<StockpileStoragePolicy.Item> expanded =
                StockpileStoragePolicy.expandSlot("Lead Glance", 0, 34, List.of());
        assertEquals(34, expanded.size());
        assertTrue(expanded.stream().allMatch(i -> i.name.equals("Lead Glance")));
    }

    @Test
    void stackResolutionIsNotAPutOrTake() {
        List<StockpileStoragePolicy.Item> gone = List.of(
                item("Lead Glance", 0), item("Lead Glance", 0), item("Lead Glance", 0));
        List<StockpileStoragePolicy.Item> gained = List.of(
                item("Lead Glance", 20), item("Lead Glance", 19), item("Lead Glance", 18));
        assertTrue(StockpileStoragePolicy.isStackResolution(gone, gained));
        assertFalse(StockpileStoragePolicy.isStackResolution(
                List.of(item("Lead Glance", 18)), List.of()));
        assertFalse(StockpileStoragePolicy.isStackResolution(
                List.of(), List.of(item("Lead Glance", 18))));
    }

    private static StockpileStoragePolicy.Item item(String name, double quality) {
        return new StockpileStoragePolicy.Item(name, quality);
    }
}
