package nurgling.db;

import nurgling.db.dao.StorageItemDao;

import java.util.ArrayList;
import java.util.List;

/**
 * After Storage Items fetch, DB rows for taken items must be dropped even if
 * the bot-opened window never bound parentGob and close-sync skipped the write.
 */
public final class FetchStorageDbSync {
    private FetchStorageDbSync() {}

    public static List<String> hashesToDelete(Iterable<StorageItemDao.StorageItemData> taken) {
        List<String> hashes = new ArrayList<>();
        if (taken == null) {
            return hashes;
        }
        for (StorageItemDao.StorageItemData item : taken) {
            if (item == null) {
                continue;
            }
            String hash = item.getItemHash();
            if (hash != null && !hash.isEmpty()) {
                hashes.add(hash);
            }
        }
        return hashes;
    }
}
