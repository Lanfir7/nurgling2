package nurgling.db.dao;

import nurgling.db.PostgresAdapter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalTimerDaoUpsertTest {
    @Test
    void staleUpsertKeepsNewerDatabaseTimer() throws Exception {
        try (SqlitePostgresAdapter adapter = SqlitePostgresAdapter.create()) {
            adapter.insertTimer(2_000L, "newer timer");

            save(adapter, 1_000L, "stale timer");

            assertEquals(2_000L, adapter.startTimeUtc());
            assertEquals("newer timer", adapter.description());
        }
    }

    @Test
    void newerUpsertReplacesOlderDatabaseTimer() throws Exception {
        try (SqlitePostgresAdapter adapter = SqlitePostgresAdapter.create()) {
            adapter.insertTimer(1_000L, "older timer");

            save(adapter, 2_000L, "newer timer");

            assertEquals(2_000L, adapter.startTimeUtc());
            assertEquals("newer timer", adapter.description());
        }
    }

    @Test
    void equalStartUpsertUpdatesTimerMetadata() throws Exception {
        try (SqlitePostgresAdapter adapter = SqlitePostgresAdapter.create()) {
            adapter.insertTimer(1_000L, "old name");

            save(adapter, 1_000L, "new name");

            assertEquals(1_000L, adapter.startTimeUtc());
            assertEquals("new name", adapter.description());
        }
    }

    private static void save(PostgresAdapter adapter, long startTimeUtc, String description)
            throws SQLException {
        new LocalTimerDao().upsert(adapter, "profile", "resource", 10L, 20, 30,
                "Timer", "gfx/terobjs/timer", startTimeUtc, 60_000L, description,
                40L, 50, 60);
    }

    /** Uses PostgreSQL's DAO path against an isolated in-memory SQLite database. */
    private static final class SqlitePostgresAdapter extends PostgresAdapter implements AutoCloseable {
        private SqlitePostgresAdapter(Connection connection) {
            super(connection);
        }

        private static SqlitePostgresAdapter create() throws SQLException {
            Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE local_timers ("
                        + "id INTEGER PRIMARY KEY, profile TEXT NOT NULL, resource_id TEXT NOT NULL, "
                        + "segment_id INTEGER, tile_x INTEGER, tile_y INTEGER, resource_name TEXT, "
                        + "resource_type TEXT, start_time_utc INTEGER, duration_ms INTEGER, "
                        + "description TEXT, grid_id INTEGER, offset_x INTEGER, offset_y INTEGER, "
                        + "created_at TEXT, updated_at TEXT, UNIQUE (profile, resource_id))");
            }
            return new SqlitePostgresAdapter(connection);
        }

        private void insertTimer(long startTimeUtc, String description) throws SQLException {
            executeUpdate("INSERT INTO local_timers (profile, resource_id, start_time_utc, description) "
                    + "VALUES (?, ?, ?, ?)", "profile", "resource", startTimeUtc, description);
        }

        private long startTimeUtc() throws SQLException {
            try (ResultSet rows = executeQuery(
                    "SELECT start_time_utc FROM local_timers WHERE profile = ? AND resource_id = ?",
                    "profile", "resource")) {
                return rows.next() ? rows.getLong(1) : -1L;
            }
        }

        private String description() throws SQLException {
            try (ResultSet rows = executeQuery(
                    "SELECT description FROM local_timers WHERE profile = ? AND resource_id = ?",
                    "profile", "resource")) {
                return rows.next() ? rows.getString(1) : null;
            }
        }

        @Override
        public void close() throws SQLException {
            getConnection().close();
        }
    }
}
