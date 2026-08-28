package nurgling.db.migration;

import nurgling.db.PostgresAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;

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

        private RecordingPostgresAdapter(Connection connection) {
            super(connection);
        }

        @Override
        public boolean tableExists(String tableName) {
            return "local_timers".equals(tableName) && localTimersExists;
        }

        @Override
        public int executeUpdate(String sql, Object... params) {
            if (sql.startsWith("CREATE TABLE local_timers")) {
                localTimersExists = true;
            }
            return 1;
        }
    }
}
