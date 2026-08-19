package nurgling.db;

import nurgling.db.dao.StorageItemDao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageItemsCompressTest {
    @Test
    void mergesSameTypeIntoOneRow() {
        StorageItemsCompress.Row q33 = row("Lead Glance", 33.02, 7, 7, "Stockpile-ore");
        StorageItemsCompress.Row q32 = row("Lead Glance", 32.52, 31, 7, "Stockpile-ore");
        StorageItemsCompress.Row iron = row("Iron Ore", 40, 2, 4, "Stockpile-ore");
        StorageItemsCompress.Row low = row("Lead Glance", 20, 5, 7, "Stockpile-ore");

        List<StorageItemsCompress.Row> afterQuality =
                StorageItemsCompress.keepQualityAtLeast(List.of(q33, q32, iron, low), 30);
        assertEquals(3, afterQuality.size());
        List<StorageItemsCompress.Row> compressed =
                StorageItemsCompress.byType(afterQuality);

        assertEquals(2, compressed.size());
        assertEquals(-1, compressed.get(0).quality);
        assertEquals("Lead Glance", compressed.get(0).name);
        assertEquals(38, compressed.get(0).items.size());
        assertEquals(7, compressed.get(0).distanceTiles);
        assertEquals("Stockpile-ore", compressed.get(0).storageName);
        assertEquals("Iron Ore", compressed.get(1).name);
        assertEquals(2, compressed.get(1).items.size());
    }

    @Test
    void keepsQualityRangeInclusiveMinExclusiveMax() {
        StorageItemsCompress.Row q33 = row("Lead Glance", 33.02, 7, 7, "Stockpile-ore");
        StorageItemsCompress.Row q32 = row("Lead Glance", 32.52, 31, 7, "Stockpile-ore");
        StorageItemsCompress.Row iron = row("Iron Ore", 40, 2, 4, "Stockpile-ore");
        StorageItemsCompress.Row low = row("Lead Glance", 20, 5, 7, "Stockpile-ore");

        List<StorageItemsCompress.Row> ranged =
                StorageItemsCompress.keepQualityRange(List.of(q33, q32, iron, low), 30.0, 40.0);

        assertEquals(2, ranged.size());
        assertEquals("Lead Glance", ranged.get(0).name);
        assertEquals(7, ranged.get(0).items.size());
        assertEquals("Lead Glance", ranged.get(1).name);
        assertEquals(31, ranged.get(1).items.size());
    }

    @Test
    void keepsEmptyList() {
        assertEquals(List.of(), StorageItemsCompress.byType(List.of()));
    }

    private static StorageItemsCompress.Row row(
            String name, double quality, int count, int dist, String storage) {
        List<StorageItemDao.StorageItemData> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new StorageItemDao.StorageItemData(
                    name + quality + i, name, quality, "(0,0)", "c" + i));
        }
        return new StorageItemsCompress.Row(name, quality, items, dist, storage);
    }
}
