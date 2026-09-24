package nurgling.db;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerLocalTimerSchemaTest {
    @Test
    void oldSchemaWithoutTimerLocationColumnsIsUnavailable() {
        PostgresAdapter legacyTable = new PostgresAdapter(null) {
            @Override
            public ResultSet executeQuery(String sql, Object... params) throws SQLException {
                if (sql.contains("grid_id") || sql.contains("offset_x") || sql.contains("offset_y")) {
                    throw new SQLException("column grid_id does not exist", "42703");
                }
                return null;
            }
        };

        assertFalse(DatabaseManager.localTimerSchemaUsable(legacyTable));
    }

    @Test
    void currentSchemaWithTimerLocationColumnsIsUsable() {
        final String[] query = new String[1];
        PostgresAdapter currentTable = new PostgresAdapter(null) {
            @Override
            public ResultSet executeQuery(String sql, Object... params) {
                query[0] = sql;
                return null;
            }
        };

        assertTrue(DatabaseManager.localTimerSchemaUsable(currentTable));
        assertTrue(query[0].contains("grid_id"));
        assertTrue(query[0].contains("offset_x"));
        assertTrue(query[0].contains("offset_y"));
    }
}
