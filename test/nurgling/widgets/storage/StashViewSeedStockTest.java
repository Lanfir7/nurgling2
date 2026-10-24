package nurgling.widgets.storage;

import haven.Coord;
import nurgling.NConfig;
import nurgling.db.dao.StorageItemDao.StorageItemData;
import nurgling.tools.StorageClassifier;
import nurgling.widgets.NStorageItemsWidget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StashViewSeedStockTest {
    @BeforeAll
    static void initFonts() {
        NConfig.getGlobalInstance();
    }

    private static StorageItemData item(String hash, double quality) {
        return new StorageItemData(hash, "Barley Seeds", quality, "1,1", "chest");
    }

    private static StorageItemData bulk(String hash, double quality, double amount, String unit) {
        return new StorageItemData(hash, "Barley Seeds", quality, "#bulk:" + amount + " " + unit, "barrel");
    }

    @Test
    void oneSeedSlotShowsSeparateItemCountAndSameUnitBarrelAmount() {
        Map<String, StashView.Stock> grouped = StashView.groupRows(List.of(
                bulk("barrel-a", 35, 20, "seeds"), item("seed-a", 45),
                bulk("barrel-b", 50, 12, "seeds"), item("seed-b", 0)));
        assertEquals(1, grouped.size());
        StashView.Stock stock = grouped.get(StorageClassifier.normalize("Barley Seeds"));
        StashView.Cell all = new StashView.Cell("Barley Seeds", null, stock, 0, Coord.z);
        assertEquals(2, all.count);
        assertEquals(Map.of("seeds", 32.0), all.bulkAmounts);
        assertEquals(50, all.maxQ);
        assertEquals(1, all.unknown);

        StashView.Cell filtered = new StashView.Cell("Barley Seeds", null, stock, 40, Coord.z);
        assertEquals(1, filtered.count);
        assertEquals(Map.of("seeds", 12.0), filtered.bulkAmounts);
        assertEquals(50, filtered.maxQ);
        assertFalse(filtered.empty());
    }

    @Test
    void qualityFilterExcludesUnknownBulkAndMixedUnitsNeverBecomeOneNumber() {
        StashView.Stock stock = StashView.groupRows(List.of(
                bulk("barrel-seeds", 0, 8, "seeds"), bulk("barrel-litres", 60, 5, "l")))
                .get(StorageClassifier.normalize("Barley Seeds"));
        StashView.Cell all = new StashView.Cell("Barley Seeds", null, stock, 0, Coord.z);
        assertTrue(all.mixedBulk());
        assertEquals(Map.of("l", 5.0, "seeds", 8.0), all.bulkAmounts);
        assertEquals(0, all.bulkAmount());
        assertFalse(all.empty());

        StashView.Cell q40 = new StashView.Cell("Barley Seeds", null, stock, 40, Coord.z);
        assertFalse(q40.mixedBulk());
        assertEquals(Map.of("l", 5.0), q40.bulkAmounts);
        assertEquals(60, q40.maxQ);
    }

    @Test
    void takePrefersFilteredCarryableItemsAndBulkOnlyRemainsUnsupported() {
        StashView.Stock stock = StashView.groupRows(List.of(
                bulk("barrel", 80, 100, "seeds"), item("low", 10), item("high", 55)))
                .get(StorageClassifier.normalize("Barley Seeds"));
        List<StorageItemData> items = stock.takeRows(40);
        assertEquals(List.of("high"), items.stream().map(StorageItemData::getItemHash).toList());
        assertNull(new NStorageItemsWidget.GroupedItem(stock.name, -1, items.size(), items).bulkUnit);

        List<StorageItemData> barrel = stock.takeRows(70);
        assertEquals(List.of("barrel"), barrel.stream().map(StorageItemData::getItemHash).toList());
        assertEquals("seeds", new NStorageItemsWidget.GroupedItem(stock.name, -1, barrel.size(), barrel).bulkUnit);
        assertTrue(stock.takeRows(90).isEmpty());
    }
}
