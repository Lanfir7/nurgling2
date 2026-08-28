package nurgling.db.migration;

import nurgling.db.PostgresAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationManagerLocalTimersRepairTest {
    @Test
    void schemaNineteenWithoutLocalTimersIsRepaired() throws Exception {
        Connection connection = connectionAtSchemaVersion(19);
        RecordingPostgresAdapter adapter = new RecordingPostgresAdapter(connection);

        new MigrationManager(connection, adapter).runMigrations();

        assertTrue(adapter.localTimersExists,
                "a database from the peer-positions-at-v12 lineage must get local_timers");
    }

    @Test
    void schemaTwentyWithLegacyLocalTimersColumnsIsRepaired() throws Exception {
        Connection connection = connectionAtSchemaVersion(20);
        RecordingPostgresAdapter adapter = new RecordingPostgresAdapter(connection,
                "id", "profile", "started_at", "duration");

        new MigrationManager(connection, adapter).runMigrations();

        assertTrue(adapter.columns.containsAll(Arrays.asList(
                "resource_id", "segment_id", "tile_x", "tile_y", "resource_name",
                "resource_type", "start_time_utc", "duration_ms", "description",
                "created_at", "updated_at")),
                "an existing legacy table must gain every column used by LocalTimerDao");
    }

    private static Connection connectionAtSchemaVersion(int version) {
        Statement statement = proxy(Statement.class, (method, args) -> {
            if (method.equals("executeQuery")) {
                String sql = (String) args[0];
                return oneRowResult(sql.contains("MAX(version)") ? version : 1);
            }
            if (method.equals("executeUpdate")) return 1;
            return defaultValue(method);
        });
        Savepoint savepoint = proxy(Savepoint.class, (method, args) -> defaultValue(method));
        return proxy(Connection.class, (method, args) -> {
            if (method.equals("createStatement")) return statement;
            if (method.equals("setSavepoint")) return savepoint;
            return defaultValue(method);
        });
    }

    private static ResultSet oneRowResult(int value) {
        boolean[] beforeFirst = {true};
        return proxy(ResultSet.class, (method, args) -> {
            if (method.equals("next")) {
                boolean next = beforeFirst[0];
                beforeFirst[0] = false;
                return next;
            }
            if (method.equals("getInt")) return value;
            if (method.equals("wasNull")) return false;
            return defaultValue(method);
        });
    }

    private static ResultSet resultWithRows(boolean hasRow) {
        boolean[] beforeFirst = {hasRow};
        return proxy(ResultSet.class, (method, args) -> {
            if (method.equals("next")) {
                boolean next = beforeFirst[0];
                beforeFirst[0] = false;
                return next;
            }
            return defaultValue(method);
        });
    }

    private static Object defaultValue(String method) {
        if (method.equals("isClosed")) return false;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, SqlInvocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method.getName(), args));
    }

    @FunctionalInterface
    private interface SqlInvocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private static final class RecordingPostgresAdapter extends PostgresAdapter {
        private boolean localTimersExists;
        private final Set<String> columns = new HashSet<>();

        private RecordingPostgresAdapter(Connection connection, String... initialColumns) {
            super(connection);
            localTimersExists = initialColumns.length > 0;
            columns.addAll(Arrays.asList(initialColumns));
        }

        @Override
        public boolean tableExists(String tableName) {
            return "local_timers".equals(tableName) && localTimersExists;
        }

        @Override
        public ResultSet executeQuery(String sql, Object... params) {
            if (sql.contains("information_schema.columns") && params.length >= 2) {
                return resultWithRows(columns.contains(String.valueOf(params[1])));
            }
            return resultWithRows(false);
        }

        @Override
        public int executeUpdate(String sql, Object... params) {
            if (sql.startsWith("CREATE TABLE local_timers")) {
                localTimersExists = true;
                columns.addAll(Arrays.asList(
                        "id", "profile", "resource_id", "segment_id", "tile_x", "tile_y",
                        "resource_name", "resource_type", "start_time_utc", "duration_ms",
                        "description", "created_at", "updated_at"));
            } else if (sql.startsWith("ALTER TABLE local_timers ADD COLUMN ")) {
                String rest = sql.substring("ALTER TABLE local_timers ADD COLUMN ".length());
                columns.add(rest.substring(0, rest.indexOf(' ')));
            }
            return 1;
        }
    }
}
