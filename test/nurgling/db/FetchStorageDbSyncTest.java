package nurgling.db;

import nurgling.db.dao.StorageItemDao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FetchStorageDbSyncTest {
    @Test
    void takenRecordsYieldTheirHashes() {
        StorageItemDao.StorageItemData a = new StorageItemDao.StorageItemData("ha", "Nettle", 10, "(0,0)", "c1");
        StorageItemDao.StorageItemData b = new StorageItemDao.StorageItemData("hb", "Nettle", 12, "(1,0)", "c1");
        assertEquals(List.of("ha", "hb"), FetchStorageDbSync.hashesToDelete(List.of(a, b)));
    }

    @Test
    void skipsNullAndEmptyHashes() {
        StorageItemDao.StorageItemData empty = new StorageItemDao.StorageItemData("", "Nettle", 10, "(0,0)", "c1");
        assertEquals(List.of(), FetchStorageDbSync.hashesToDelete(List.of(empty)));
    }
}
