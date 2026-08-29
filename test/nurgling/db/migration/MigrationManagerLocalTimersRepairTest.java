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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void existingIndexesDoNotAbortRepairMigration() throws Exception {
        TransactionState transaction = new TransactionState();
        Connection connection = connectionAtSchemaVersion(19, transaction);
        RecordingPostgresAdapter adapter = new RecordingPostgresAdapter(connection, transaction,
                "id", "profile", "resource_id", "segment_id", "tile_x", "tile_y",
                "resource_name", "resource_type", "start_time_utc", "duration_ms",
                "description", "created_at", "updated_at");

        Map<Integer, String> skipped = new MigrationManager(connection, adapter).runMigrations();

        assertTrue(skipped.isEmpty(),
                "already-existing indexes must not skip either local-timers repair migration");
        assertFalse(transaction.aborted,
                "the migration must leave the PostgreSQL transaction usable");
        assertTrue(transaction.savepointRollbacks > 0,
                "duplicate indexes must be recovered through a savepoint rollback");
        assertEquals(0, transaction.fullRollbacks,
                "duplicate indexes must not roll back the whole migration");
    }

    private static Connection connectionAtSchemaVersion(int version) {
        return connectionAtSchemaVersion(version, null);
    }

    private static Connection connectionAtSchemaVersion(int version, TransactionState transaction) {
        Statement statement = proxy(Statement.class, (method, args) -> {
            if (transaction != null && transaction.aborted
                    && (method.equals("executeQuery") || method.equals("executeUpdate"))) {
                throw abortedTransaction();
            }
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
            if (method.equals("rollback") && transaction != null) {
                transaction.aborted = false;
                if (args == null || args.length == 0) {
                    transaction.fullRollbacks++;
                } else {
                    transaction.savepointRollbacks++;
                }
            }
            if (method.equals("commit") && transaction != null && transaction.aborted) {
                throw abortedTransaction();
            }
            return defaultValue(method);
        });
    }

    private static java.sql.SQLException abortedTransaction() {
        return new java.sql.SQLException(
                "current transaction is aborted, commands ignored until end of transaction block",
                "25P02");
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

    private static final class TransactionState {
        private boolean aborted;
        private int savepointRollbacks;
        private int fullRollbacks;
    }

    private static final class RecordingPostgresAdapter extends PostgresAdapter {
        private boolean localTimersExists;
        private final Set<String> columns = new HashSet<>();
        private final TransactionState transaction;

        private RecordingPostgresAdapter(Connection connection, String... initialColumns) {
            this(connection, null, initialColumns);
        }

        private RecordingPostgresAdapter(Connection connection, TransactionState transaction,
                                         String... initialColumns) {
            super(connection);
            this.transaction = transaction;
            localTimersExists = initialColumns.length > 0;
            columns.addAll(Arrays.asList(initialColumns));
        }

        @Override
        public boolean tableExists(String tableName) {
            return "local_timers".equals(tableName) && localTimersExists;
        }

        @Override
        public ResultSet executeQuery(String sql, Object... params) throws java.sql.SQLException {
            if (transaction != null && transaction.aborted) throw abortedTransaction();
            if (sql.contains("information_schema.columns") && params.length >= 2) {
                return resultWithRows(columns.contains(String.valueOf(params[1])));
            }
            return resultWithRows(false);
        }

        @Override
        public int executeUpdate(String sql, Object... params) throws java.sql.SQLException {
            if (transaction != null && transaction.aborted) throw abortedTransaction();
            if (transaction != null && sql.startsWith("CREATE ") && sql.contains(" INDEX ")) {
                transaction.aborted = true;
                throw new java.sql.SQLException("relation already exists", "42P07");
            }
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
