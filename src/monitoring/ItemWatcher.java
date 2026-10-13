package monitoring;

import haven.Coord;
import haven.Utils;
import nurgling.NUtils;
import nurgling.db.DatabaseManager;
import nurgling.db.dao.StorageItemDao;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ItemWatcher implements Runnable {

    // Cache of container hashes with their item signature (hash of all item hashes)
    // Key = containerHash, Value = combined hash of all items in that container
    private static final ConcurrentHashMap<String, String> containerItemCache = new ConcurrentHashMap<>();
    private static final int MAX_CONTAINER_CACHE_SIZE = 1000;
    
    /**
     * Get current container cache size for debug display
     */
    public static int getContainerCacheSize() {
        return containerItemCache.size();
    }
    
    /**
     * Clear cached signature for a container to force re-sync on next close
     */
    public static void invalidateContainerCache(String containerHash) {
        if (containerHash != null) {
            containerItemCache.remove(containerHash);
        }
    }

    public static class ItemInfo {
        public String name;
        public double q = -1;
        public Coord c;           // Coordinates in inventory (or stack parent coords if in stack)
        public String container;
        public int stackIndex;    // Index in stack (-1 if not in stack)

        public ItemInfo(String name, double q, Coord c, String container, int stackIndex) {
            this.name = name;
            this.q = Double.parseDouble(Utils.odformat2(q, 2));
            this.c = c;
            this.container = container;
            this.stackIndex = stackIndex;
        }
        
        // Backward compatible constructor
        public ItemInfo(String name, double q, Coord c, String container) {
            this(name, q, c, container, -1);
        }
    }

    /**
     * One storageitems row as written. Containers build these from ItemInfo; stockpiles, barrels and
     * cisterns build their own (unknown quality is null, coordinates carry the "#pile:"/"#bulk:" tag).
     */
    public static class Row {
        public final String hash;
        public final String name;
        public final Double quality;
        public final String coordinates;

        public Row(String hash, String name, Double quality, String coordinates) {
            this.hash = hash;
            this.name = name;
            this.quality = quality;
            this.coordinates = coordinates;
        }
    }

    private final DatabaseManager databaseManager;
    private final ArrayList<ItemInfo> iis;
    private final List<Row> rows; // pre-built rows that replace the whole container, or null
    private final String containerHash; // Store container hash separately for empty cache case
    private final long gridId;      // with coord: the containers row to upsert alongside, if coord != null
    private final String coord;
    private final long pileObservation;

    public ItemWatcher(ArrayList<ItemInfo> iis, DatabaseManager databaseManager, String containerHash) {
        this.iis = iis;
        this.rows = null;
        this.databaseManager = databaseManager;
        this.containerHash = containerHash;
        this.gridId = 0;
        this.coord = null;
        this.pileObservation = 0;
    }
    
    // Backward compatible constructor (deprecated - use new one with containerHash)
    public ItemWatcher(ArrayList<ItemInfo> iis, DatabaseManager databaseManager) {
        this.iis = iis;
        this.rows = null;
        this.databaseManager = databaseManager;
        this.containerHash = (iis != null && !iis.isEmpty()) ? iis.get(0).container : null;
        this.gridId = 0;
        this.coord = null;
        this.pileObservation = 0;
    }

    private ItemWatcher(List<Row> rows, DatabaseManager databaseManager, String containerHash, long gridId, String coord) {
        this.iis = null;
        this.rows = rows;
        this.databaseManager = databaseManager;
        this.containerHash = containerHash;
        this.gridId = gridId;
        this.coord = coord;
        this.pileObservation = rows.isEmpty() || isPileSnapshot(rows)
                ? StockpileStorageTracker.nextPileObservation() : 0;
    }

    /**
     * Replace a container snapshot. Pile counts are reconciled with exact item observations already
     * stored; liquid rows replace exactly. With a coord, the container location is written atomically.
     */
    public static ItemWatcher replacing(List<Row> rows, DatabaseManager databaseManager, String containerHash,
                                        long gridId, String coord) {
        return new ItemWatcher(rows, databaseManager, containerHash, gridId, coord);
    }

    @Override
    public void run() {
        sync();
    }

    /** Persist the snapshot and report whether the database is now in sync. */
    public boolean sync() {
        if (containerHash == null) {
            return false;
        }
        
        // Items without a positive quality (stacks and unqualified items) are not stored for containers
        final List<Row> rows = (this.rows != null) ? this.rows : rowsFromItems();
        final boolean replaceAll = (this.rows != null);

        boolean isEmpty = rows.isEmpty();
        
        // Build a signature of all items in this container (empty string if no items)
        String itemsSignature = isEmpty ? "" : buildItemsSignature(rows);
        
        // Check if this container already has the same items (skip duplicate write)
        String cachedSignature = containerItemCache.get(containerHash);
        if (!replaceAll && itemsSignature.equals(cachedSignature)) {
            nurgling.db.DatabaseManager.incrementSkippedContainer();
            return true; // Same items, no need to write to DB
        }

        try {
            databaseManager.executeOperation(adapter -> {
                if (coord != null && !isEmpty) {
                    new nurgling.db.dao.ContainerDao().saveContainer(adapter, containerHash, gridId, coord);
                }
                // The window knows the pile's total, while transfer tracking knows individual
                // names and qualities. Read and reconcile under the same DB transaction as the
                // replacement, after any earlier queued transfer has committed.
                List<Row> writeRows = rows;
                if (replaceAll && isPileSnapshot(rows)) {
                    List<StorageItemDao.StorageItemData> existing =
                            new StorageItemDao().loadStorageItemsByContainer(adapter, containerHash);
                    writeRows = reconcilePileSnapshot(rows, existing, containerHash);
                }
                if (isEmpty || replaceAll) {
                    replaceRows(adapter, containerHash, writeRows);
                } else {
                    // Delete items that are NOT in the cache
                    deleteItems(adapter, rows);
                    insertItems(adapter, rows, containerHash);
                }
                return null;
            });
            if (pileObservation != 0)
                StockpileStorageTracker.noteCommittedPileCount(containerHash, pileObservation, rows.size());
            
            // Update cache after successful write
            if (containerItemCache.size() >= MAX_CONTAINER_CACHE_SIZE) {
                // Simple eviction - remove random entries
                int toRemove = MAX_CONTAINER_CACHE_SIZE / 4;
                java.util.Iterator<String> it = containerItemCache.keySet().iterator();
                while (it.hasNext() && toRemove > 0) {
                    it.next();
                    it.remove();
                    toRemove--;
                }
            }
            containerItemCache.put(containerHash, itemsSignature);
            
            // Clear search query cache so next search will query fresh data
            NGlobalSearchItems.clearQueryCache();
            
            // Notify that container data has changed - increment version for debounced refresh
            nurgling.tools.NSearchItem.notifyContainerDataChanged();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean isPileSnapshot(List<Row> rows) {
        if (rows.isEmpty()) return false;
        for (Row row : rows)
            if (row.coordinates == null || !row.coordinates.startsWith("#pile:")) return false;
        return true;
    }

    /** Keep observed item identities when the pile count still accommodates them. */
    static List<Row> reconcilePileSnapshot(List<Row> snapshot,
                                            List<StorageItemDao.StorageItemData> existing,
                                            String containerHash) {
        if (!isPileSnapshot(snapshot)) return snapshot;
        List<Row> candidates = new ArrayList<>();
        for (StorageItemDao.StorageItemData item : existing) {
            if (!item.isBulk())
                candidates.add(new Row(item.getItemHash(), item.getName(),
                        item.hasQuality() ? item.getQuality() : null, item.getCoordinates()));
        }
        // A count-only shrink cannot identify which old items survived. A transfer delta that
        // identified the removed item has already reduced the stored row count before this read.
        if (snapshot.size() < candidates.size())
            candidates.clear();
        return reconcilePileCount(candidates, snapshot.size(), snapshot.get(0).name, containerHash);
    }

    /** Reduce a pile to its observed count, preserving only identities that can still fit. */
    static List<Row> reconcilePileCount(List<Row> candidates, int count, String fallbackName,
                                         String containerHash) {
        if (count <= 0) return new ArrayList<>();
        List<Row> precise = new ArrayList<>();
        String knownName = null;
        boolean oneKnownName = true;
        for (Row item : candidates) {
            String coordinates = item.coordinates;
            if (coordinates == null || coordinates.startsWith("#pile:")
                    || coordinates.startsWith("#bulk:")) continue;
            precise.add(item);
            if (item.name == null || item.name.isEmpty()) {
                oneKnownName = false;
            } else if (knownName == null) {
                knownName = item.name;
            } else if (!knownName.equals(item.name)) {
                oneKnownName = false;
            }
        }
        String unknownName = oneKnownName && knownName != null ? knownName : fallbackName;
        // When the count shrinks below the number of observed identities, we cannot know
        // which ones survived. Discard their qualities instead of reporting an invented survivor.
        if (precise.size() > count) precise.clear();
        precise.sort((a, b) -> a.hash.compareTo(b.hash));
        List<Row> result = new ArrayList<>(count);
        result.addAll(precise);
        for (int i = 0, missing = count - precise.size(); i < missing; i++) {
            String tag = "#pile:" + i;
            result.add(new Row(NUtils.calculateSHA256(containerHash + "|" + unknownName + "|" + tag),
                    unknownName, null, tag));
        }
        return result;
    }
    
    /**
     * Build a hash signature representing all items in this container
     */
    private String buildItemsSignature(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        // Sort to ensure consistent signature regardless of item order. A barrel keeps one row
        // under one hash while its amount changes, so the signature covers the content too.
        rows.stream()
            .map(r -> r.hash + "|" + r.name + "|" + r.quality + "|" + r.coordinates)
            .sorted()
            .forEach(sb::append);
        return NUtils.calculateSHA256(sb.toString());
    }

    private List<Row> rowsFromItems() {
        List<Row> result = new ArrayList<>();
        if (iis == null) return result;
        for (ItemInfo item : iis) {
            if (item.q > 0) {
                result.add(new Row(generateItemHash(item), item.name, item.q, item.c.toString()));
            }
        }
        return result;
    }

    /**
     * Delete ALL items for this container (used when cache is empty)
     */
    static void replaceRows(nurgling.db.DatabaseAdapter adapter, String containerHash,
                            List<Row> rows) throws SQLException {
        deleteAllContainerItems(adapter, containerHash);
        insertItems(adapter, rows, containerHash);
    }

    private static void deleteAllContainerItems(nurgling.db.DatabaseAdapter adapter,
                                                String containerHash) throws SQLException {
        String deleteSql = "DELETE FROM storageitems WHERE container = ?";
        adapter.executeUpdate(deleteSql, containerHash);
    }
    
    private void deleteItems(nurgling.db.DatabaseAdapter adapter, List<Row> rows) throws SQLException {
        if (rows.isEmpty()) return;
        
        // Build parameterized IN clause: DELETE ... WHERE ... NOT IN (?, ?, ?, ...)
        String placeholders = rows.stream().map(i -> "?").collect(java.util.stream.Collectors.joining(","));
        String deleteSql = "DELETE FROM storageitems WHERE container = ? AND item_hash NOT IN (" + placeholders + ")";

        Object[] params = new Object[rows.size() + 1];
        params[0] = containerHash;

        // Set each item hash as a separate parameter
        for (int i = 0; i < rows.size(); i++) {
            params[i + 1] = rows.get(i).hash;
        }

        adapter.executeUpdate(deleteSql, params);
    }

    private static void insertItems(nurgling.db.DatabaseAdapter adapter, List<Row> rows,
                                    String containerHash) throws SQLException {
        if (rows.isEmpty()) return;
        
        // Use batch upsert for efficient bulk insert
        java.util.List<String> columns = java.util.List.of("item_hash", "name", "quality", "coordinates", "container");
        java.util.List<String> conflictColumns = java.util.List.of("item_hash");
        java.util.List<String> updateColumns = java.util.List.of("name", "quality", "coordinates", "container");
        
        String batchSql = adapter.getBatchUpsertSql("storageitems", columns, conflictColumns, updateColumns);
        
        // Prepare batch parameters
        java.util.List<Object[]> paramList = new java.util.ArrayList<>(rows.size());
        for (Row row : rows) {
            paramList.add(new Object[]{row.hash, row.name, row.quality, row.coordinates, containerHash});
        }
        
        // Execute batch insert - much more efficient than individual inserts
        adapter.executeBatch(batchSql, paramList);
    }

    private String generateItemHash(ItemInfo item) {
        // Hash includes: name + coords + quality + stackIndex
        // stackIndex ensures items in same stack with same quality have different hashes
        String data = item.name + item.c.toString() + item.q + "_" + item.stackIndex;
        return NUtils.calculateSHA256(data);
    }
}
