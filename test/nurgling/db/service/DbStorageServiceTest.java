package nurgling.db.service;

import nurgling.db.SqliteAdapter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DbStorageServiceTest {
    @Test
    void sharedMapCleanupKeepsUserMarkersOutOfDeletionScope() {
        List<String> tables = DbStorageService.sharedMapTables();

        assertEquals(List.of("map_grids", "map_grid_placements"), tables);
        assertFalse(tables.contains("map_markers"));
    }

    @Test
    void diskUsageUsesReadableBinaryUnits() {
        assertEquals("?", DbStorageService.humanBytes(-1));
        assertEquals("1023 B", DbStorageService.humanBytes(1023));
        assertEquals("1.0 KB", DbStorageService.humanBytes(1024));
        assertEquals("1.5 MB", DbStorageService.humanBytes(1572864));
    }

    @Test
    void postCommitVacuumFailureKeepsOriginalCountWithoutRepeatingDeletes() throws Exception {
        try (Connection real = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = real.createStatement()) {
                statement.execute("CREATE TABLE map_grids(id INTEGER)");
                statement.execute("CREATE TABLE map_grid_placements(id INTEGER)");
                statement.execute("INSERT INTO map_grids VALUES (1), (2)");
                statement.execute("INSERT INTO map_grid_placements VALUES (1)");
            }
            real.setAutoCommit(false);
            CountingSqliteAdapter adapter = new CountingSqliteAdapter(
                    failVacuumAfterCommit(real));

            int deletedGrids = DbStorageService.clearSqlite(adapter);

            assertEquals(2, deletedGrids);
            assertEquals(2, adapter.deleteExecutions);
            assertEquals(0, count(real, "map_grids"));
            assertEquals(0, count(real, "map_grid_placements"));
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }

    private static Connection failVacuumAfterCommit(Connection real) {
        AtomicBoolean committed = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                DbStorageServiceTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("commit".equals(method.getName())) {
                        Object result = invoke(real, method, args);
                        committed.set(true);
                        return result;
                    }
                    if (committed.get() && "createStatement".equals(method.getName())) {
                        Statement statement = (Statement) invoke(real, method, args);
                        return failVacuum(statement);
                    }
                    return invoke(real, method, args);
                });
    }

    private static Statement failVacuum(Statement real) {
        return (Statement) Proxy.newProxyInstance(
                DbStorageServiceTest.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName()) && args != null
                            && args.length > 0 && "VACUUM".equals(args[0])) {
                        throw new SQLException("injected post-commit VACUUM failure");
                    }
                    return invoke(real, method, args);
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static class CountingSqliteAdapter extends SqliteAdapter {
        int deleteExecutions;

        CountingSqliteAdapter(Connection connection) {
            super(connection);
        }

        @Override
        public int executeUpdate(String sql, Object... params) throws SQLException {
            if (sql.startsWith("DELETE FROM ")) {
                deleteExecutions++;
            }
            return super.executeUpdate(sql, params);
        }
    }
}
