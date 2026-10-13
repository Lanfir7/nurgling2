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
}
