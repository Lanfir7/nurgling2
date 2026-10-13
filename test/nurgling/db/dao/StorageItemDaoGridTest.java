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
