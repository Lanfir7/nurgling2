package nurgling.db.dao;

import nurgling.db.SqliteAdapter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerDaoWipeTest {
    @Test
    void deleteAllContainersAlsoRemovesStoredItems() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE containers(hash VARCHAR(64) PRIMARY KEY, grid_id BIGINT, coord VARCHAR(255))");
                statement.execute("CREATE TABLE storageitems(item_hash VARCHAR(64) PRIMARY KEY, name VARCHAR(255), quality DOUBLE, coordinates VARCHAR(255), container VARCHAR(64))");
                statement.execute("INSERT INTO containers VALUES ('box', 1, '(0, 0)')");
                statement.execute("INSERT INTO storageitems VALUES ('item', 'Rope', 10, '(0, 0)', 'box')");
            }

            new ContainerDao().deleteAllContainers(new SqliteAdapter(connection));

            assertEquals(0, count(connection, "storageitems"));
            assertEquals(0, count(connection, "containers"));
        }
    }

    private static int count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }
}
