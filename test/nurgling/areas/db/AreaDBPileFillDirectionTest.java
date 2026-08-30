package nurgling.areas.db;

import nurgling.areas.NArea;
import nurgling.areas.PileFillDirection;
import nurgling.areas.storage.AreaDBStorage;
import nurgling.areas.storage.DatabaseConnectionManager;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaDBPileFillDirectionTest {
    @Test
    void migrationDefaultsOldRowsAndStorageRoundTripsDirection() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            conn.setAutoCommit(false);
            new AreasDBMigrationManager(conn).runMigrations();

            assertEquals("LEFT_TO_RIGHT", columnDefault(conn, "pile_fill_direction"));

            AreaDBStorage storage = new AreaDBStorage(managerFor(conn));
            NArea area = validArea(41, PileFillDirection.RIGHT_TO_LEFT);
            storage.saveArea(area);
            assertEquals(PileFillDirection.RIGHT_TO_LEFT,
                    storage.loadAllAreas().get(41).pileFillDirection);

            area.pileFillDirection = PileFillDirection.TOP_TO_BOTTOM;
            storage.saveArea(area);
            assertEquals(PileFillDirection.TOP_TO_BOTTOM,
                    storage.loadAllAreas().get(41).pileFillDirection);

            area.synced = true;
            storage.saveArea(area);
            assertEquals(PileFillDirection.TOP_TO_BOTTOM,
                    storage.loadAllAreas().get(41).pileFillDirection);

            storage.deleteArea(41);
            area.pileFillDirection = PileFillDirection.BOTTOM_TO_TOP;
            storage.saveArea(area);
            assertEquals(PileFillDirection.BOTTOM_TO_TOP,
                    storage.loadAllAreas().get(41).pileFillDirection);
        }
    }

    private static DatabaseConnectionManager managerFor(Connection connection) {
        return new DatabaseConnectionManager() {
            @Override
            public Connection getConnection() {
                return connection;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }

    private static NArea validArea(int id, PileFillDirection direction) {
        NArea area = new NArea("round-trip area");
        area.id = id;
        area.space = new NArea.Space();
        area.color = new Color(12, 34, 56, 78);
        area.jin = new JSONArray();
        area.jout = new JSONArray();
        area.pileFillDirection = direction;
        return area;
    }

    private static String columnDefault(Connection connection, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(areas)")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    String value = rs.getString("dflt_value");
                    return value == null ? null : value.replace("'", "").replace("\"", "");
                }
            }
        }
        return null;
    }
}
