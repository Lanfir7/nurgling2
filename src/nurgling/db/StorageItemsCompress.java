package nurgling.db;

import nurgling.db.dao.StorageItemDao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapse storage-item rows into one per item type.
 * Quality filtering must already have been applied.
 */
public final class StorageItemsCompress {
    private StorageItemsCompress() {}

    public static final class Row {
        public final String name;
        public final double quality;
        public final List<StorageItemDao.StorageItemData> items;
        public final int distanceTiles;
        public final String storageName;

        public Row(String name, double quality, List<StorageItemDao.StorageItemData> items,
                   int distanceTiles, String storageName) {
            this.name = name;
            this.quality = quality;
            this.items = items;
            this.distanceTiles = distanceTiles;
            this.storageName = storageName;
        }
    }

    public static List<Row> keepQualityAtLeast(List<Row> rows, double minQuality) {
        return keepQualityRange(rows, minQuality, null);
    }

    public static List<Row> keepQualityBelow(List<Row> rows, double maxExclusive) {
        return keepQualityRange(rows, null, maxExclusive);
    }

    public static List<Row> keepQualityRange(List<Row> rows, Double minInclusive, Double maxExclusive) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (minInclusive == null && maxExclusive == null) {
            return rows;
        }
        List<Row> out = new ArrayList<>();
        for (Row row : rows) {
            List<StorageItemDao.StorageItemData> kept = new ArrayList<>();
            for (StorageItemDao.StorageItemData item : row.items) {
                double q = item.getQuality();
                if (minInclusive != null && q < minInclusive) {
                    continue;
                }
                if (maxExclusive != null && q >= maxExclusive) {
                    continue;
                }
                kept.add(item);
            }
            if (!kept.isEmpty()) {
                out.add(new Row(row.name, row.quality, kept, row.distanceTiles, row.storageName));
            }
        }
        return out;
    }

    public static List<Row> byType(List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<Row>> byName = new LinkedHashMap<>();
        for (Row row : rows) {
            byName.computeIfAbsent(row.name, k -> new ArrayList<>()).add(row);
        }
        List<Row> out = new ArrayList<>();
        for (List<Row> group : byName.values()) {
            List<StorageItemDao.StorageItemData> items = new ArrayList<>();
            List<String> storageNames = new ArrayList<>();
            int bestDist = StorageTableInfo.UNKNOWN_DIST;
            String nearestName = "—";
            for (Row row : group) {
                items.addAll(row.items);
                storageNames.add(row.storageName);
                if (row.distanceTiles >= 0 && (bestDist < 0 || row.distanceTiles < bestDist)) {
                    bestDist = row.distanceTiles;
                    nearestName = row.storageName;
                }
            }
            out.add(new Row(
                    group.get(0).name,
                    -1,
                    items,
                    bestDist,
                    StorageTableInfo.storageLabel(nearestName, storageNames)));
        }
        return out;
    }
}
