package nurgling.db;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseManagerLocalTimerSchemaTest {
    @Test
    void missingDaoColumnsMakeLocalTimerSchemaUnavailable() {
        PostgresAdapter legacyTable = new PostgresAdapter(null) {
            @Override
            public ResultSet executeQuery(String sql, Object... params) throws SQLException {
                throw new SQLException("column resource_id does not exist", "42703");
            }
        };

        assertFalse(DatabaseManager.localTimerSchemaUsable(legacyTable));
    }
}
