package nurgling.areas.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер миграций для БД зон.
 * Использует только миграции для таблиц зон (areas, area_spaces, area_inputs, area_outputs, area_specialisations).
 * Всегда использует SQLite (isPostgres = false).
 */
public class AreasDBMigrationManager {
    private final Connection connection;
    private final boolean isPostgres = false; // БД зон всегда SQLite

    public AreasDBMigrationManager(Connection connection) {
        this.connection = connection;
    }

    public void runMigrations() throws SQLException {
        boolean versionTableExists = checkVersionTableExists();
        int currentVersion = 0;
        
        if (versionTableExists) {
            currentVersion = getCurrentVersion();
        }
        
        List<Migration> migrations = getMigrations();
        for (Migration migration : migrations) {
            if (migration.version > currentVersion) {
                System.out.println("AreasDBMigrationManager: Running migration version " + migration.version + ": " + migration.description);
                try {
                    migration.run(connection, isPostgres);
                    
                    // Create version table if it doesn't exist yet (after first migration)
                    if (!versionTableExists) {
                        ensureVersionTableExists();
                        versionTableExists = true;
                    }
                    
                    updateVersion(migration.version);
                    connection.commit();
                    System.out.println("AreasDBMigrationManager: Migration " + migration.version + " completed successfully");
                } catch (SQLException e) {
                    connection.rollback();
                    System.err.println("AreasDBMigrationManager: Migration " + migration.version + " failed: " + e.getMessage());
                    throw e;
                }
            }
        }
    }

    private boolean checkVersionTableExists() throws SQLException {
        String sql = isPostgres
            ? "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'schema_version')"
            : "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (isPostgres) {
                return rs.next() && rs.getBoolean(1);
            } else {
                return rs.next();
            }
        }
    }

    private int getCurrentVersion() throws SQLException {
        String sql = "SELECT version FROM schema_version ORDER BY version DESC LIMIT 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("version");
            }
        }
        return 0;
    }

    private void ensureVersionTableExists() throws SQLException {
        String sql = isPostgres
            ? "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            : "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void updateVersion(int version) throws SQLException {
        String sql = isPostgres
            ? "INSERT INTO schema_version (version) VALUES (?) ON CONFLICT (version) DO NOTHING"
            : "INSERT OR IGNORE INTO schema_version (version) VALUES (?)";
        
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, version);
            stmt.executeUpdate();
        }
    }

    private List<Migration> getMigrations() {
        List<Migration> migrations = new ArrayList<>();
        
        // Migration 2: Create areas tables (пропускаем миграцию 1 для кукбука)
        migrations.add(new Migration(2, "Create areas tables for zone storage") {
            @Override
            public void run(Connection conn, boolean isPostgres) throws SQLException {
                Statement stmt = conn.createStatement();
                
                // Create areas table
                String createAreas = isPostgres
                    ? "CREATE TABLE IF NOT EXISTS areas (" +
                      "id INTEGER PRIMARY KEY, " +
                      "global_id VARCHAR(64) UNIQUE, " +
                      "name VARCHAR(255) NOT NULL, " +
                      "path VARCHAR(512), " +
                      "color_r INT DEFAULT 194, " +
                      "color_g INT DEFAULT 194, " +
                      "color_b INT DEFAULT 65, " +
                      "color_a INT DEFAULT 56, " +
                      "hide BOOLEAN DEFAULT FALSE, " +
                      "owner_id VARCHAR(64), " +
                      "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                      "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                      "sync_version INTEGER DEFAULT 1, " +
                      "sync_status VARCHAR(20) DEFAULT 'local', " +
                      "last_sync_at TIMESTAMP, " +
                      "deleted BOOLEAN DEFAULT FALSE" +
                      ")"
                    : "CREATE TABLE IF NOT EXISTS areas (" +
                      "id INTEGER PRIMARY KEY, " +
                      "global_id VARCHAR(64) UNIQUE, " +
                      "name VARCHAR(255) NOT NULL, " +
                      "path VARCHAR(512), " +
                      "color_r INTEGER DEFAULT 194, " +
                      "color_g INTEGER DEFAULT 194, " +
                      "color_b INTEGER DEFAULT 65, " +
                      "color_a INTEGER DEFAULT 56, " +
                      "hide INTEGER DEFAULT 0, " +
                      "owner_id VARCHAR(64), " +
                      "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                      "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                      "sync_version INTEGER DEFAULT 1, " +
                      "sync_status VARCHAR(20) DEFAULT 'local', " +
                      "last_sync_at TIMESTAMP, " +
                      "deleted INTEGER DEFAULT 0" +
                      ")";
                
                stmt.executeUpdate(createAreas);
                System.out.println("AreasDBMigrationManager: Created areas table");
                
                // Create area_spaces table
                String createAreaSpaces = "CREATE TABLE IF NOT EXISTS area_spaces (" +
                    "id " + (isPostgres ? "SERIAL" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ", " +
                    "area_id INTEGER NOT NULL, " +
                    "grid_id BIGINT NOT NULL, " +
                    "begin_x INTEGER NOT NULL, " +
                    "begin_y INTEGER NOT NULL, " +
                    "end_x INTEGER NOT NULL, " +
                    "end_y INTEGER NOT NULL, " +
                    "FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE CASCADE" +
                    ")";
                stmt.executeUpdate(createAreaSpaces);
                System.out.println("AreasDBMigrationManager: Created area_spaces table");
                
                // Create area_inputs table
                String createAreaInputs = "CREATE TABLE IF NOT EXISTS area_inputs (" +
                    "id " + (isPostgres ? "SERIAL" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ", " +
                    "area_id INTEGER NOT NULL, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "type VARCHAR(50), " +
                    "icon_data TEXT, " +
                    "FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE CASCADE" +
                    ")";
                stmt.executeUpdate(createAreaInputs);
                System.out.println("AreasDBMigrationManager: Created area_inputs table");
                
                // Create area_outputs table
                String createAreaOutputs = "CREATE TABLE IF NOT EXISTS area_outputs (" +
                    "id " + (isPostgres ? "SERIAL" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ", " +
                    "area_id INTEGER NOT NULL, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "type VARCHAR(50), " +
                    "th INTEGER, " +
                    "icon_data TEXT, " +
                    "FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE CASCADE" +
                    ")";
                stmt.executeUpdate(createAreaOutputs);
                System.out.println("AreasDBMigrationManager: Created area_outputs table");
                
                // Create area_specialisations table
                String createAreaSpecialisations = "CREATE TABLE IF NOT EXISTS area_specialisations (" +
                    "id " + (isPostgres ? "SERIAL" : "INTEGER PRIMARY KEY AUTOINCREMENT") + ", " +
                    "area_id INTEGER NOT NULL, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "subtype VARCHAR(255), " +
                    "FOREIGN KEY (area_id) REFERENCES areas(id) ON DELETE CASCADE" +
                    ")";
                stmt.executeUpdate(createAreaSpecialisations);
                System.out.println("AreasDBMigrationManager: Created area_specialisations table");
                
                // Create indexes
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_area_spaces_area_id ON area_spaces(area_id)");
                System.out.println("AreasDBMigrationManager: Created indexes for areas tables");
                
                stmt.close();
            }
        });
        
        // Migration 3: Add icon_data column to area_inputs and area_outputs
        migrations.add(new Migration(3, "Add icon_data column to area_inputs and area_outputs tables") {
            @Override
            public void run(Connection conn, boolean isPostgres) throws SQLException {
                Statement stmt = conn.createStatement();
                
                // Check if icon_data column already exists in area_inputs
                boolean iconDataExistsInputs = false;
                try {
                    if (isPostgres) {
                        stmt.executeQuery("SELECT icon_data FROM area_inputs LIMIT 1").close();
                    } else {
                        stmt.executeQuery("SELECT icon_data FROM area_inputs LIMIT 1").close();
                    }
                    iconDataExistsInputs = true;
                    System.out.println("AreasDBMigrationManager: icon_data column in area_inputs already exists");
                } catch (SQLException e) {
                    // Column doesn't exist, need to add it
                }
                
                if (!iconDataExistsInputs) {
                    try {
                        stmt.executeUpdate("ALTER TABLE area_inputs ADD COLUMN icon_data TEXT");
                        System.out.println("AreasDBMigrationManager: Added icon_data column to area_inputs table");
                    } catch (SQLException e) {
                        if (e.getMessage() != null && !e.getMessage().contains("duplicate column")) {
                            throw e;
                        }
                        System.out.println("AreasDBMigrationManager: icon_data column in area_inputs already exists");
                    }
                }
                
                // Check if icon_data column already exists in area_outputs
                boolean iconDataExistsOutputs = false;
                try {
                    if (isPostgres) {
                        stmt.executeQuery("SELECT icon_data FROM area_outputs LIMIT 1").close();
                    } else {
                        stmt.executeQuery("SELECT icon_data FROM area_outputs LIMIT 1").close();
                    }
                    iconDataExistsOutputs = true;
                    System.out.println("AreasDBMigrationManager: icon_data column in area_outputs already exists");
                } catch (SQLException e) {
                    // Column doesn't exist, need to add it
                }
                
                if (!iconDataExistsOutputs) {
                    try {
                        stmt.executeUpdate("ALTER TABLE area_outputs ADD COLUMN icon_data TEXT");
                        System.out.println("AreasDBMigrationManager: Added icon_data column to area_outputs table");
                    } catch (SQLException e) {
                        if (e.getMessage() != null && !e.getMessage().contains("duplicate column")) {
                            throw e;
                        }
                        System.out.println("AreasDBMigrationManager: icon_data column in area_outputs already exists");
                    }
                }
                
                stmt.close();
            }
        });
        
        // Migration 4: Add zone_sync column for server synchronization
        migrations.add(new Migration(4, "Add zone_sync column for server synchronization") {
            @Override
            public void run(Connection conn, boolean isPostgres) throws SQLException {
                Statement stmt = conn.createStatement();
                
                // Check if zone_sync column already exists
                boolean zoneSyncExists = false;
                try {
                    if (isPostgres) {
                        stmt.executeQuery("SELECT zone_sync FROM areas LIMIT 1").close();
                    } else {
                        stmt.executeQuery("SELECT zone_sync FROM areas LIMIT 1").close();
                    }
                    zoneSyncExists = true;
                    System.out.println("AreasDBMigrationManager: zone_sync column already exists");
                } catch (SQLException e) {
                    // Column doesn't exist, need to add it
                }
                
                if (!zoneSyncExists) {
                    try {
                        stmt.executeUpdate("ALTER TABLE areas ADD COLUMN zone_sync VARCHAR(100)");
                        System.out.println("AreasDBMigrationManager: Added zone_sync column to areas table");
                    } catch (SQLException e) {
                        if (e.getMessage() != null && !e.getMessage().contains("duplicate column")) {
                            throw e;
                        }
                        System.out.println("AreasDBMigrationManager: zone_sync column already exists");
                    }
                }
                
                stmt.close();
            }
        });
        
        // Migration 5: Update existing zones with NULL zone_sync from config
        migrations.add(new Migration(5, "Update existing zones with NULL zone_sync from config") {
            @Override
            public void run(Connection conn, boolean isPostgres) throws SQLException {
                Statement stmt = conn.createStatement();
                
                try {
                    // Получаем zone_sync из настроек
                    Object zoneSyncObj = nurgling.NConfig.get(nurgling.NConfig.Key.syncZoneSync);
                    String zoneSync = null;
                    if (zoneSyncObj != null && zoneSyncObj instanceof String) {
                        zoneSync = ((String) zoneSyncObj).trim();
                        if (zoneSync.isEmpty()) {
                            zoneSync = null;
                        }
                    }
                    
                    if (zoneSync != null && !zoneSync.isEmpty()) {
                        // Обновляем все зоны с NULL zone_sync
                        String updateSql = "UPDATE areas SET zone_sync = ? WHERE zone_sync IS NULL OR zone_sync = ''";
                        try (java.sql.PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setString(1, zoneSync);
                            int updated = updateStmt.executeUpdate();
                            if (updated > 0) {
                                System.out.println("AreasDBMigrationManager: Updated " + updated + " zones with zone_sync: " + zoneSync);
                            }
                        }
                    } else {
                        System.out.println("AreasDBMigrationManager: zone_sync not set in config, skipping migration 5");
                    }
                } catch (Exception e) {
                    System.out.println("AreasDBMigrationManager: Migration 5 warning: " + e.getMessage() + " (continuing...)");
                }
                
                stmt.close();
            }
        });
        
        // Migration 6: Remove UNIQUE constraint from global_id to allow NULL values
        migrations.add(new Migration(6, "Remove UNIQUE constraint from global_id to allow NULL values") {
            @Override
            public void run(Connection conn, boolean isPostgres) throws SQLException {
                Statement stmt = conn.createStatement();
                
                try {
                    // SQLite не поддерживает ALTER TABLE для удаления UNIQUE constraint напрямую
                    // Нужно пересоздать таблицу
                    if (!isPostgres) {
                        // Создаем временную таблицу без UNIQUE constraint
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS areas_new (" +
                            "id INTEGER PRIMARY KEY, " +
                            "global_id VARCHAR(64), " +  // Без UNIQUE
                            "name VARCHAR(255) NOT NULL, " +
                            "path VARCHAR(512), " +
                            "color_r INTEGER DEFAULT 194, " +
                            "color_g INTEGER DEFAULT 194, " +
                            "color_b INTEGER DEFAULT 65, " +
                            "color_a INTEGER DEFAULT 56, " +
                            "hide INTEGER DEFAULT 0, " +
                            "owner_id VARCHAR(64), " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "sync_version INTEGER DEFAULT 1, " +
                            "sync_status VARCHAR(20) DEFAULT 'local', " +
                            "last_sync_at TIMESTAMP, " +
                            "deleted INTEGER DEFAULT 0, " +
                            "zone_sync VARCHAR(100)" +
                            ")");
                        
                        // Копируем данные
                        stmt.executeUpdate("INSERT INTO areas_new SELECT * FROM areas");
                        
                        // Удаляем старую таблицу
                        stmt.executeUpdate("DROP TABLE areas");
                        
                        // Переименовываем новую таблицу
                        stmt.executeUpdate("ALTER TABLE areas_new RENAME TO areas");
                        
                        // Создаем индекс для быстрого поиска по global_id (но не UNIQUE)
                        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_areas_global_id ON areas(global_id)");
                        
                        System.out.println("AreasDBMigrationManager: Removed UNIQUE constraint from global_id");
                    } else {
                        // PostgreSQL - можно использовать ALTER TABLE
                        // Но проще пересоздать таблицу аналогично SQLite
                        System.out.println("AreasDBMigrationManager: PostgreSQL migration for global_id not needed (handled by app logic)");
                    }
                } catch (SQLException e) {
                    // Проверяем, есть ли уже индекс без UNIQUE (значит миграция уже применена)
                    try {
                        stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='areas'").close();
                        // Если ошибка не критична, продолжаем
                        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                        if (errorMsg.contains("no such table") || 
                            errorMsg.contains("duplicate column") ||
                            errorMsg.contains("already exists")) {
                            System.out.println("AreasDBMigrationManager: Migration 5 already applied or not needed: " + e.getMessage());
                        } else {
                            // Проверяем, может быть UNIQUE constraint уже удален
                            System.out.println("AreasDBMigrationManager: Migration 5 warning: " + e.getMessage() + " (continuing...)");
                        }
                    } catch (SQLException checkEx) {
                        // Если не можем проверить, просто логируем и продолжаем
                        System.out.println("AreasDBMigrationManager: Migration 5 check failed: " + checkEx.getMessage() + " (continuing...)");
                    }
                }
                
                stmt.close();
            }
        });
        
        return migrations;
    }
    
    abstract static class Migration {
        final int version;
        final String description;

        Migration(int version, String description) {
            this.version = version;
            this.description = description;
        }

        abstract void run(Connection conn, boolean isPostgres) throws SQLException;
    }
}

