package nurgling.cookbook;

import nurgling.db.dao.StorageItemDao;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class StockTest {
    @Test
    void countsOnlyPositiveBulkAndKeepsOrdinaryItemsSeparate() {
        Stock stock = new Stock(Arrays.asList(
                new StorageItemDao.StorageItemData("a", "Milk", 12, "#bulk:2.5 l", "barrel", 1L),
                new StorageItemDao.StorageItemData("b", "Milk", 12, "#bulk:0 l", "barrel", 1L),
                new StorageItemDao.StorageItemData("c", "Carrot", 8, "(0, 0)", "box", 1L)));

        assertTrue(stock.keys().contains(GenericTypes.stockKey("Milk")));
        assertEquals("2.5 l", stock.amount(Collections.singletonList("Milk")));
        assertEquals("1", stock.amount(Collections.singletonList("Carrot")));
        assertNull(new Stock(Collections.singletonList(
                new StorageItemDao.StorageItemData("empty", "Milk", 12, "#bulk:0 l", "barrel", 1L)))
                .amount(Collections.singletonList("Milk")));
    }

    @Test
    void countableKeysNeverOfferBulkOnlyRowsAsFetchableItems() {
        Stock stock = new Stock(Arrays.asList(
                new StorageItemDao.StorageItemData("milk-barrel", "Milk", 10, "#bulk:12 l", "barrel", 1L),
                new StorageItemDao.StorageItemData("seed-barrel", "Barley Seed", 0, "#bulk:50 seeds", "barrel", 1L),
                new StorageItemDao.StorageItemData("seed-item", "Barley Seed", 15, "(1, 1)", "cupboard", 1L),
                new StorageItemDao.StorageItemData("carrot", "Carrot", 5, "(2, 1)", "cupboard", 1L)));

        assertFalse(stock.countableKeys().contains(GenericTypes.stockKey("Milk")));
        assertTrue(stock.countableKeys().contains(GenericTypes.stockKey("Barley Seed")));
        assertTrue(stock.countableKeys().contains(GenericTypes.stockKey("Carrot")));
        assertThrows(UnsupportedOperationException.class,
                () -> stock.countableKeys().add(GenericTypes.stockKey("Apple")));
    }
}
