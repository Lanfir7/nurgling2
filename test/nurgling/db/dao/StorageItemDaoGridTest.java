package nurgling.db.dao;

import nurgling.db.SqliteAdapter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageItemDaoGridTest {
    @Test
    void countableLookupUsesExactNamesAndLocatedContainersOnly() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE containers(hash TEXT PRIMARY KEY, grid_id BIGINT, coord TEXT)");
                statement.execute("CREATE TABLE storageitems(item_hash TEXT PRIMARY KEY, name TEXT, quality DOUBLE, coordinates TEXT, container TEXT)");
                statement.execute("INSERT INTO containers VALUES ('chest', 42, '(100, -50)')");
                statement.execute("INSERT INTO storageitems VALUES ('one', 'Barley Seed', 20, '(0, 0)', 'chest')");
                statement.execute("INSERT INTO storageitems VALUES ('two', 'Barley Seed', 20, '#bulk:500 seeds', 'chest')");
                statement.execute("INSERT INTO storageitems VALUES ('three', 'Barley Seedling', 20, '(1, 0)', 'chest')");
                statement.execute("INSERT INTO storageitems VALUES ('four', 'Barley Seed', 20, '(2, 0)', 'missing')");
            }
            List<StorageItemDao.LocatedItem> rows = new StorageItemDao()
                    .loadLocatedCountableByExactNames(new SqliteAdapter(connection), List.of("Barley Seed"));
            assertEquals(1, rows.size());
            assertEquals("Barley Seed", rows.get(0).name);
            assertEquals("chest", rows.get(0).container);
            assertEquals(42, rows.get(0).gridId);
            assertEquals("(100, -50)", rows.get(0).coord);
        }
    }

    @Test
    void loadsGridWithoutLosingBulkOrOrphanRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE containers(hash TEXT PRIMARY KEY, grid_id BIGINT, coord TEXT)");
                statement.execute("CREATE TABLE storageitems(item_hash TEXT PRIMARY KEY, name TEXT, quality DOUBLE, coordinates TEXT, container TEXT)");
                statement.execute("INSERT INTO containers VALUES ('barrel', 42, '(0, 0)')");
                statement.execute("INSERT INTO storageitems VALUES ('bulk', 'Milk', 23, '#bulk:12.5 l', 'barrel')");
                statement.execute("INSERT INTO storageitems VALUES ('orphan', 'Rope', 0, NULL, 'missing')");
            }

            List<StorageItemDao.StorageItemData> rows = new StorageItemDao()
                    .loadAllStorageItems(new SqliteAdapter(connection));
            assertEquals(2, rows.size());
            StorageItemDao.StorageItemData bulk = rows.stream()
                    .filter(row -> "bulk".equals(row.getItemHash())).findFirst().orElseThrow(AssertionError::new);
            assertEquals(Long.valueOf(42), bulk.getGridId());
            assertEquals("#bulk:12.5 l", bulk.getCoordinates());
            assertTrue(bulk.isBulk());
            assertEquals("l", bulk.getBulkUnit());
            assertEquals(12.5, bulk.getBulkAmount(), 0.001);
            assertEquals(23, bulk.getQuality(), 0.001);

            StorageItemDao.StorageItemData orphan = rows.stream()
                    .filter(row -> "orphan".equals(row.getItemHash())).findFirst().orElseThrow(AssertionError::new);
            assertNull(orphan.getGridId());
            assertNull(orphan.getCoordinates());
            assertFalse(orphan.isBulk());
        }
    }
}
