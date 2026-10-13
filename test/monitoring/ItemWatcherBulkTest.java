package monitoring;

import org.junit.jupiter.api.Test;
import nurgling.db.dao.StorageItemDao;
import nurgling.db.StockpileStoragePolicy;
import nurgling.db.DatabaseAdapter;
import nurgling.db.SqliteAdapter;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemWatcherBulkTest {
    @Test
    void bulkAmountAndUnknownQualityArePartOfTheSnapshotSignature() throws Exception {
        ItemWatcher.Row ten = new ItemWatcher.Row("barrel-row", "Water", null, "#bulk:10.00 l");
        ItemWatcher.Row twenty = new ItemWatcher.Row("barrel-row", "Water", null, "#bulk:20.00 l");
        ItemWatcher.Row qualified = new ItemWatcher.Row("barrel-row", "Water", 37.0, "#bulk:10.00 l");

        assertNotEquals(signature(Arrays.asList(ten)), signature(Arrays.asList(twenty)));
        assertNotEquals(signature(Arrays.asList(ten)), signature(Arrays.asList(qualified)));
    }

    @Test
    void snapshotSignatureDoesNotDependOnRowOrder() throws Exception {
        ItemWatcher.Row first = new ItemWatcher.Row("pile-0", "Flax Seeds", null, "#pile:0");
        ItemWatcher.Row second = new ItemWatcher.Row("pile-1", "Flax Seeds", null, "#pile:1");
        assertEquals(signature(Arrays.asList(first, second)), signature(Arrays.asList(second, first)));
    }

    @Test
    void closingPilePreservesQualifiedObservationsWithoutDuplicatingCount() {
        List<ItemWatcher.Row> result = reconcile(2, precise("one", "Flax Seeds", 32),
                precise("two", "Flax Seeds", 41));
        assertEquals(2, result.size());
        assertEquals("one", result.get(0).hash);
        assertEquals(32.0, result.get(0).quality);
        assertEquals("two", result.get(1).hash);
        assertEquals(41.0, result.get(1).quality);
    }

    @Test
    void partialPileKeepsKnownRowsAndAddsOnlyUnknownRemainder() {
        List<ItemWatcher.Row> result = reconcile(3, precise("known", "Flax Seeds", 50));
        assertEquals(3, result.size());
        assertEquals("known", result.get(0).hash);
        assertEquals("Flax Seeds", result.get(1).name);
        assertNull(result.get(1).quality);
        assertEquals("#pile:0", result.get(1).coordinates);
        assertNull(result.get(2).quality);
    }

    @Test
    void zeroPileClearsEverything() {
        assertEquals(0, reconcile(0, precise("known", "Flax Seeds", 50)).size());
    }

    @Test
    void shrinkingPileDowngradesAmbiguousQualityAndKeepsExactCount() {
        List<ItemWatcher.Row> result = reconcile(1, precise("one", "Flax Seeds", 32),
                precise("two", "Flax Seeds", 41));
        assertEquals(1, result.size());
        assertEquals("Generic Pile", result.get(0).name);
        assertNull(result.get(0).quality);
    }

    @Test
    void countOnlyShrinkOfKnownAndUnknownDoesNotClaimKnownSurvivor() {
        List<ItemWatcher.Row> result = reconcile(1, precise("known", "Flax Seeds", 54),
                pile("unknown", "Generic Pile", 0));
        assertEquals(1, result.size());
        assertEquals("Generic Pile", result.get(0).name);
        assertNull(result.get(0).quality);
    }

    @Test
    void liquidsStillReplaceExactly() {
        ItemWatcher.Row water = new ItemWatcher.Row("bulk", "Water", null, "#bulk:12.00 l");
        List<ItemWatcher.Row> result = ItemWatcher.reconcilePileSnapshot(
                Arrays.asList(water), Arrays.asList(precise("old", "Flax Seeds", 50)), "container");
        assertEquals(1, result.size());
        assertEquals("bulk", result.get(0).hash);
    }

    @Test
    void lateDepositReplacesCountedPlaceholderWithQualifiedIdentity() {
        List<ItemWatcher.Row> rows = StockpileStorageTracker.reconcilePileDelta(
                Arrays.asList(pile("old", "Generic Pile", 0)),
                Arrays.asList(new StockpileStoragePolicy.Item("Flax Seeds", 54)),
                java.util.Collections.emptyList(), 1, "Flax Seeds", "late-deposit");
        assertEquals(1, rows.size());
        assertEquals("Flax Seeds", rows.get(0).name);
        assertEquals(54.0, rows.get(0).quality);
    }

    @Test
    void snapshotAfterDepositPreservesCommittedQuality() {
        List<ItemWatcher.Row> delta = StockpileStorageTracker.reconcilePileDelta(
                java.util.Collections.emptyList(),
                Arrays.asList(new StockpileStoragePolicy.Item("Flax Seeds", 54)),
                java.util.Collections.emptyList(), 1, "Flax Seeds", "delta-first");
        List<ItemWatcher.Row> snapshot = ItemWatcher.reconcilePileSnapshot(
                Arrays.asList(new ItemWatcher.Row("placeholder", "Generic Pile", null, "#pile:0")),
                Arrays.asList(stored(delta.get(0))), "delta-first");
        assertEquals(1, snapshot.size());
        assertEquals(54.0, snapshot.get(0).quality);
    }

    @Test
    void lateWithdrawalAfterCountedSnapshotDoesNotUndercount() {
        List<ItemWatcher.Row> rows = StockpileStorageTracker.reconcilePileDelta(
                Arrays.asList(precise("withdrawn", "Flax Seeds", 54)),
                java.util.Collections.emptyList(),
                Arrays.asList(new StockpileStoragePolicy.Item("Flax Seeds", 54)),
                1, "Flax Seeds", "late-withdrawal");
        assertEquals(1, rows.size());
        assertEquals("Flax Seeds", rows.get(0).name);
        assertNull(rows.get(0).quality);
    }

    @Test
    void noWindowDepositAfterOlderSnapshotIncreasesCount() {
        String hash = "no-window-deposit-" + System.nanoTime();
        long snapshot = StockpileStorageTracker.nextPileObservation();
        StockpileStorageTracker.noteCommittedPileCount(hash, snapshot, 1);
        long transferStarted = StockpileStorageTracker.nextPileObservation();
        Integer fallback = StockpileStorageTracker.newerCommittedPileCount(hash, transferStarted, null);
        assertNull(fallback);
        List<ItemWatcher.Row> result = StockpileStorageTracker.reconcilePileDelta(
                Arrays.asList(pile("old", "Generic Pile", 0)),
                Arrays.asList(new StockpileStoragePolicy.Item("Flax Seeds", 54)),
                java.util.Collections.emptyList(), fallback, "Flax Seeds", hash);
        assertEquals(2, result.size());
        assertEquals(54.0, result.get(0).quality);
    }

    @Test
    void noWindowWithdrawalAfterOlderSnapshotDecreasesCount() {
        String hash = "no-window-withdraw-" + System.nanoTime();
        long snapshot = StockpileStorageTracker.nextPileObservation();
        StockpileStorageTracker.noteCommittedPileCount(hash, snapshot, 2);
        long transferStarted = StockpileStorageTracker.nextPileObservation();
        Integer fallback = StockpileStorageTracker.newerCommittedPileCount(hash, transferStarted, null);
        assertNull(fallback);
        List<ItemWatcher.Row> result = StockpileStorageTracker.reconcilePileDelta(
                Arrays.asList(pile("old-0", "Flax Seeds", 0), pile("old-1", "Flax Seeds", 1)),
                java.util.Collections.emptyList(),
                Arrays.asList(new StockpileStoragePolicy.Item("Flax Seeds", 54)),
                fallback, "Flax Seeds", hash);
        assertEquals(1, result.size());
        assertNull(result.get(0).quality);
    }

    @Test
    void oldDelayedDeltaCannotReduceNewerCountedSnapshot() {
        String hash = "newer-snapshot-" + System.nanoTime();
        long old = StockpileStorageTracker.nextPileObservation();
        long newer = StockpileStorageTracker.nextPileObservation();
        StockpileStorageTracker.noteCommittedPileCount(hash, newer, 8);
        Integer resolved = StockpileStorageTracker.newerCommittedPileCount(hash, old, 7);
        assertEquals(8, resolved);
        List<StorageItemDao.StorageItemData> existing = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) existing.add(pile("pile-" + i, "Generic Pile", i));
        List<ItemWatcher.Row> result = StockpileStorageTracker.reconcilePileDelta(existing,
                Arrays.asList(new StockpileStoragePolicy.Item("Flax Seeds", 54)),
                java.util.Collections.emptyList(), resolved, "Flax Seeds", hash);
        assertEquals(8, result.size());
        assertEquals(54.0, result.get(0).quality);
    }

    @Test
    void replacementRollsBackAsAUnitAndCanBeRetried() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE storageitems (item_hash TEXT PRIMARY KEY, name TEXT, "
                        + "quality REAL, coordinates TEXT, container TEXT)");
            }
            DatabaseAdapter adapter = new SqliteAdapter(connection);
            ItemWatcher.replaceRows(adapter, "pile", Arrays.asList(
                    new ItemWatcher.Row("known", "Flax Seeds", 54.0, "(1, 0)")));
            connection.commit();

            ItemWatcher.replaceRows(adapter, "pile", Arrays.asList(
                    new ItemWatcher.Row("unknown", "Flax Seeds", null, "#pile:0")));
            connection.rollback();
            assertEquals("known", storedHash(connection));

            ItemWatcher.replaceRows(adapter, "pile", Arrays.asList(
                    new ItemWatcher.Row("unknown", "Flax Seeds", null, "#pile:0")));
            connection.commit();
            assertEquals("unknown", storedHash(connection));
        }
    }

    private static List<ItemWatcher.Row> reconcile(int count, StorageItemDao.StorageItemData... existing) {
        List<ItemWatcher.Row> snapshot = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++)
            snapshot.add(new ItemWatcher.Row("placeholder-" + i, "Generic Pile", null, "#pile:" + i));
        return ItemWatcher.reconcilePileSnapshot(snapshot, Arrays.asList(existing), "container");
    }

    private static StorageItemDao.StorageItemData precise(String hash, String name, double q) {
        return new StorageItemDao.StorageItemData(hash, name, q, "(1, 0)", "container");
    }

    private static StorageItemDao.StorageItemData pile(String hash, String name, int slot) {
        return new StorageItemDao.StorageItemData(hash, name, 0, "#pile:" + slot, "container");
    }

    private static StorageItemDao.StorageItemData stored(ItemWatcher.Row row) {
        return new StorageItemDao.StorageItemData(row.hash, row.name,
                row.quality == null ? 0 : row.quality, row.coordinates, "container");
    }

    private static String storedHash(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT item_hash FROM storageitems WHERE container='pile'")) {
            String hash = rows.next() ? rows.getString(1) : null;
            assertEquals(false, rows.next());
            return hash;
        }
    }

    private static String signature(List<ItemWatcher.Row> rows) throws Exception {
        ItemWatcher watcher = ItemWatcher.replacing(rows, null, "container", 0, null);
        Method method = ItemWatcher.class.getDeclaredMethod("buildItemsSignature", List.class);
        method.setAccessible(true);
        return (String) method.invoke(watcher, rows);
    }
}
