package nurgling.db;

import nurgling.db.dao.StorageItemDao;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Selects stored items whose container is in the recorded village. */
public final class StorageVillageFilter {
    private StorageVillageFilter() {
    }

    public static List<StorageItemDao.StorageItemData> apply(
            List<StorageItemDao.StorageItemData> items, boolean onlyVillage, Set<Long> village) {
        // With no recorded village, hiding every row would make the storage window useless.
        if (!onlyVillage || village == null || village.isEmpty())
            return items;
        return items.stream()
                .filter(item -> item.getGridId() != null && village.contains(item.getGridId()))
                .collect(Collectors.toList());
    }

    /** Remove confirmed database deletions from the backing list before any filter is toggled. */
    public static List<StorageItemDao.StorageItemData> withoutHashes(
            List<StorageItemDao.StorageItemData> items, Set<String> deletedHashes) {
        if (deletedHashes.isEmpty())
            return items;
        return items.stream()
                .filter(item -> !deletedHashes.contains(item.getItemHash()))
                .collect(Collectors.toList());
    }
}
