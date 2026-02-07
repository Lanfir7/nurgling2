package nurgling.db.migration;

import nurgling.db.DatabaseAdapter;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Database migration manager that handles schema updates
 */
public class MigrationManager {
    private final Connection connection;
    private final DatabaseAdapter adapter;

    public MigrationManager(Connection connection, DatabaseAdapter adapter) {
        this.connection = connection;
        this.adapter = adapter;
    }

    public void runMigrations() throws SQLException {
        boolean versionTableExists = checkVersionTableExists();
        int currentVersion = 0;

        if (versionTableExists) {
            currentVersion = getCurrentVersion();
        }

        List<Migration> migrations = getMigrations();
        System.out.println("Current schema version: " + currentVersion + ", available migrations: " + migrations.size());
        for (Migration migration : migrations) {
            if (migration.version > currentVersion) {
                System.out.println("Running migration version " + migration.version + ": " + migration.description);
                try {
                    migration.run(adapter);

                    // Create version table if it doesn't exist yet (after first migration)
                    if (!versionTableExists) {
                        ensureVersionTableExists();
                        versionTableExists = true;
                    }

                    updateVersion(migration.version);
                    connection.commit();
                    System.out.println("Migration " + migration.version + " completed successfully");
                } catch (SQLException e) {
                    connection.rollback();
                    System.err.println("Migration " + migration.version + " failed: " + e.getMessage());
                    throw e;
                }
            }
        }
    }

    private boolean checkVersionTableExists() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version LIMIT 1");
            rs.close();
            stmt.close();
            return true;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                // Ignore rollback errors
            }
            return false;
        }
    }

    private void ensureVersionTableExists() throws SQLException {
        String createTableQuery = "CREATE TABLE schema_version (" +
                                 "version INTEGER PRIMARY KEY, " +
                                 "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                                 ")";
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(createTableQuery);
        stmt.close();
        System.out.println("Created schema_version table");
    }

    private int getCurrentVersion() throws SQLException {
        String query = "SELECT MAX(version) as max_version FROM schema_version";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                int version = rs.getInt("max_version");
                return rs.wasNull() ? 0 : version;
            }
        }
        return 0;
    }

    private void updateVersion(int version) throws SQLException {
        String query = adapter instanceof nurgling.db.PostgresAdapter
            ? "INSERT INTO schema_version (version) VALUES (" + version + ")"
            : "INSERT INTO schema_version (version) VALUES (" + version + ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(query);
        }
    }

    private List<Migration> getMigrations() {
        List<Migration> migrations = new ArrayList<>();

        migrations.add(new Migration(1, "Initial migration: create favorite_recipes table and add UNIQUE constraints") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                // Create favorite_recipes table if it doesn't exist
                if (!adapter.tableExists("favorite_recipes")) {
                    String createFavoriteRecipes = "CREATE TABLE favorite_recipes (" +
                                                  "recipe_hash VARCHAR(64) PRIMARY KEY REFERENCES recipes (recipe_hash) ON DELETE CASCADE" +
                                                  ")";
                    adapter.executeUpdate(createFavoriteRecipes);
                    System.out.println("Created favorite_recipes table");
                }

                // Add UNIQUE constraints for ingredients and feps
                if (adapter instanceof nurgling.db.PostgresAdapter) {
                    // For PostgreSQL, add unique constraints
                    try {
                        adapter.executeUpdate("ALTER TABLE ingredients ADD CONSTRAINT ingredients_unique UNIQUE (recipe_hash, name)");
                        System.out.println("Added UNIQUE constraint to ingredients table");
                    } catch (SQLException e) {
                        if (e.getSQLState().equals("42P07") || e.getMessage().contains("already exists")) {
                            System.out.println("UNIQUE constraint on ingredients already exists");
                        } else {
                            throw e;
                        }
                    }

                    try {
                        adapter.executeUpdate("ALTER TABLE feps ADD CONSTRAINT feps_unique UNIQUE (recipe_hash, name)");
                        System.out.println("Added UNIQUE constraint to feps table");
                    } catch (SQLException e) {
                        if (e.getSQLState().equals("42P07") || e.getMessage().contains("already exists")) {
                            System.out.println("UNIQUE constraint on feps already exists");
                        } else {
                            throw e;
                        }
                    }
                } else {
                    // For SQLite, check if constraints already exist
                    ensureSqliteUniqueConstraints(adapter);
                }
            }
        });

        migrations.add(new Migration(2, "Add resource_name column to ingredients table for layered sprites") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                // Check if column already exists using proper metadata query
                boolean columnExists = false;
                if (adapter instanceof nurgling.db.PostgresAdapter) {
                    // PostgreSQL: use information_schema with explicit schema
                    try (ResultSet rs = adapter.executeQuery(
                            "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'ingredients' AND column_name = 'resource_name'")) {
                        columnExists = rs.next();
                    }
                } else {
                    // SQLite: use pragma
                    try (ResultSet rs = adapter.executeQuery("PRAGMA table_info(ingredients)")) {
                        while (rs.next()) {
                            if ("resource_name".equals(rs.getString("name"))) {
                                columnExists = true;
                                break;
                            }
                        }
                    }
                }
                
                if (columnExists) {
                    System.out.println("resource_name column already exists in ingredients table");
                } else {
                    adapter.executeUpdate("ALTER TABLE ingredients ADD COLUMN resource_name VARCHAR(512)");
                    System.out.println("Added resource_name column to ingredients table");
                }
            }
        });

        migrations.add(new Migration(3, "Create areas table for shared area storage") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                // Create areas table if it doesn't exist
                if (!adapter.tableExists("areas")) {
                    String createAreasSql = "CREATE TABLE areas (" +
                            "id INTEGER PRIMARY KEY, " +
                            "name VARCHAR(255) NOT NULL, " +
                            "path VARCHAR(512) DEFAULT '', " +
                            "hide " + (adapter instanceof nurgling.db.PostgresAdapter ? "BOOLEAN" : "INTEGER") + " DEFAULT " + 
                                (adapter instanceof nurgling.db.PostgresAdapter ? "FALSE" : "0") + ", " +
                            "color_r INTEGER DEFAULT 194, " +
                            "color_g INTEGER DEFAULT 194, " +
                            "color_b INTEGER DEFAULT 65, " +
                            "color_a INTEGER DEFAULT 56, " +
                            "data TEXT NOT NULL, " +  // JSON data for space, in, out, spec
                            "profile VARCHAR(255) DEFAULT 'global', " +  // profile/genus for filtering
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")";
                    adapter.executeUpdate(createAreasSql);
                    System.out.println("Created areas table");

                    // Create index for faster profile-based queries
                    String createIndexSql = "CREATE INDEX idx_areas_profile ON areas (profile)";
                    adapter.executeUpdate(createIndexSql);
                    System.out.println("Created index on areas.profile");
                }
            }
        });

        migrations.add(new Migration(4, "Add version column to areas table") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                // Check if column already exists using proper metadata query
                boolean columnExists = false;
                if (adapter instanceof nurgling.db.PostgresAdapter) {
                    // PostgreSQL: use information_schema with explicit schema
                    try (ResultSet rs = adapter.executeQuery(
                            "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'areas' AND column_name = 'version'")) {
                        columnExists = rs.next();
                    }
                } else {
                    // SQLite: use pragma
                    try (ResultSet rs = adapter.executeQuery("PRAGMA table_info(areas)")) {
                        while (rs.next()) {
                            if ("version".equals(rs.getString("name"))) {
                                columnExists = true;
                                break;
                            }
                        }
                    }
                }
                
                if (columnExists) {
                    System.out.println("version column already exists in areas table");
                } else {
                    adapter.executeUpdate("ALTER TABLE areas ADD COLUMN version INTEGER DEFAULT 1");
                    System.out.println("Added version column to areas table");
                }
            }
        });

        migrations.add(new Migration(5, "Create routes table for shared route storage") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                if (!adapter.tableExists("routes")) {
                    String createRoutesSql = "CREATE TABLE routes (" +
                            "id INTEGER NOT NULL, " +
                            "name VARCHAR(255) NOT NULL, " +
                            "path VARCHAR(512) DEFAULT '', " +
                            "data TEXT NOT NULL, " +  // JSON data for waypoints, spec
                            "profile VARCHAR(255) NOT NULL, " +
                            "version INTEGER DEFAULT 1, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "PRIMARY KEY (id, profile)" +
                            ")";
                    adapter.executeUpdate(createRoutesSql);
                    System.out.println("Created routes table");

                    String createIndexSql = "CREATE INDEX idx_routes_profile ON routes (profile)";
                    adapter.executeUpdate(createIndexSql);
                    System.out.println("Created index on routes.profile");
                }
            }
        });

        migrations.add(new Migration(6, "Add deleted column to areas table for soft delete support") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                // Check if column already exists
                boolean columnExists = false;
                if (adapter instanceof nurgling.db.PostgresAdapter) {
                    // PostgreSQL: use information_schema with explicit schema
                    try (ResultSet rs = adapter.executeQuery(
                            "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'areas' AND column_name = 'deleted'")) {
                        columnExists = rs.next();
                    }
                } else {
                    // SQLite: use pragma
                    try (ResultSet rs = adapter.executeQuery("PRAGMA table_info(areas)")) {
                        while (rs.next()) {
                            if ("deleted".equals(rs.getString("name"))) {
                                columnExists = true;
                                break;
                            }
                        }
                    }
                }
                
                if (columnExists) {
                    System.out.println("deleted column already exists in areas table");
                } else {
                    // Add deleted column with default value
                    if (adapter instanceof nurgling.db.PostgresAdapter) {
                        adapter.executeUpdate("ALTER TABLE areas ADD COLUMN deleted BOOLEAN DEFAULT FALSE NOT NULL");
                    } else {
                        adapter.executeUpdate("ALTER TABLE areas ADD COLUMN deleted INTEGER DEFAULT 0 NOT NULL");
                    }
                    System.out.println("Added deleted column to areas table");
                }
            }
        });

        migrations.add(new Migration(7, "Create animal_markers table for Postgres (animal discovery markers)") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
                    return;
                }
                if (adapter.tableExists("animal_markers")) {
                    System.out.println("animal_markers table already exists");
                    return;
                }
                String createSql = "CREATE TABLE animal_markers (" +
                    "id SERIAL PRIMARY KEY, " +
                    "profile VARCHAR(255) NOT NULL, " +
                    "gob_id BIGINT NOT NULL, " +
                    "animal_type VARCHAR(128), " +
                    "display_name VARCHAR(255), " +
                    "segment_id BIGINT NOT NULL, " +
                    "tile_x INTEGER NOT NULL, " +
                    "tile_y INTEGER NOT NULL, " +
                    "grid_id BIGINT, " +
                    "local_tile_x INTEGER, " +
                    "local_tile_y INTEGER, " +
                    "quality DOUBLE PRECISION, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE (profile, gob_id)" +
                    ")";
                adapter.executeUpdate(createSql);
                adapter.executeUpdate("CREATE INDEX idx_animal_markers_profile ON animal_markers (profile)");
                adapter.executeUpdate("CREATE INDEX idx_animal_markers_profile_gob ON animal_markers (profile, gob_id)");
                System.out.println("Created animal_markers table");
            }
        });

        migrations.add(new Migration(8, "Add killed_at, killed_by to animal_markers") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                if (!(adapter instanceof nurgling.db.PostgresAdapter)) return;
                if (!adapter.tableExists("animal_markers")) return;
                try {
                    adapter.executeUpdate("ALTER TABLE animal_markers ADD COLUMN IF NOT EXISTS killed_at TIMESTAMP");
                    adapter.executeUpdate("ALTER TABLE animal_markers ADD COLUMN IF NOT EXISTS killed_by VARCHAR(255)");
                    System.out.println("Added killed_at, killed_by to animal_markers");
                } catch (SQLException e) {
                    if (e.getMessage() != null && !e.getMessage().contains("already exists"))
                        throw e;
                }
            }
        });

        migrations.add(new Migration(9, "Add icon_path to animal_markers (saved icon path for reload)") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                if (!(adapter instanceof nurgling.db.PostgresAdapter)) return;
                if (!adapter.tableExists("animal_markers")) return;
                try {
                    adapter.executeUpdate("ALTER TABLE animal_markers ADD COLUMN IF NOT EXISTS icon_path VARCHAR(512)");
                    System.out.println("Added icon_path to animal_markers");
                } catch (SQLException e) {
                    if (e.getMessage() != null && !e.getMessage().contains("already exists"))
                        throw e;
                }
            }
        });

        migrations.add(new Migration(10, "Create local_timers table for Postgres (resource timers like tar pit, clay pit)") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                if (!(adapter instanceof nurgling.db.PostgresAdapter)) {
                    return;
                }
                if (adapter.tableExists("local_timers")) {
                    System.out.println("local_timers table already exists");
                    return;
                }
                // All times stored as UTC milliseconds to avoid timezone issues
                // Server is GMT+0, clients may be GMT+2 or other timezones
                String createSql = "CREATE TABLE local_timers (" +
                    "id SERIAL PRIMARY KEY, " +
                    "profile VARCHAR(255) NOT NULL, " +
                    "resource_id VARCHAR(512) NOT NULL, " +  // Unique ID: res_segmentId_x_y_resourceType
                    "segment_id BIGINT NOT NULL, " +
                    "tile_x INTEGER NOT NULL, " +
                    "tile_y INTEGER NOT NULL, " +
                    "resource_name VARCHAR(255), " +
                    "resource_type VARCHAR(512), " +  // e.g., gfx/terobjs/map/tarpit
                    "start_time_utc BIGINT NOT NULL, " +  // Unix timestamp in milliseconds (UTC)
                    "duration_ms BIGINT NOT NULL, " +  // Duration in milliseconds
                    "description VARCHAR(512), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE (profile, resource_id)" +
                    ")";
                adapter.executeUpdate(createSql);
                adapter.executeUpdate("CREATE INDEX idx_local_timers_profile ON local_timers (profile)");
                adapter.executeUpdate("CREATE INDEX idx_local_timers_profile_resource ON local_timers (profile, resource_id)");
                adapter.executeUpdate("CREATE INDEX idx_local_timers_expiration ON local_timers (profile, start_time_utc, duration_ms)");
                System.out.println("Created local_timers table");
            }
        });

        migrations.add(new Migration(11, "Create craft_recipes table for ingredient-to-recipe lookup") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                if (adapter.tableExists("craft_recipes")) {
                    System.out.println("craft_recipes table already exists");
                    return;
                }
                String createSql = "CREATE TABLE craft_recipes (" +
                    "ingredient_name VARCHAR(255) NOT NULL, " +
                    "pagina_resource VARCHAR(512) NOT NULL, " +
                    "recipe_name VARCHAR(255) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (ingredient_name, pagina_resource)" +
                    ")";
                adapter.executeUpdate(createSql);
                adapter.executeUpdate("CREATE INDEX idx_craft_recipes_ingredient ON craft_recipes (ingredient_name)");
                System.out.println("Created craft_recipes table");
            }
        });

        migrations.add(new Migration(12, "Recreate craft_recipes with mapping_type (input/output separation)") {
            @Override
            public void run(DatabaseAdapter adapter) throws SQLException {
                // Drop old table — it's a cache, will be repopulated when recipes are opened
                adapter.executeUpdate("DROP TABLE IF EXISTS craft_recipes");
                adapter.executeUpdate("DROP INDEX IF EXISTS idx_craft_recipes_ingredient");
                String createSql = "CREATE TABLE craft_recipes (" +
                    "item_name VARCHAR(255) NOT NULL, " +
                    "pagina_resource VARCHAR(512) NOT NULL, " +
                    "recipe_name VARCHAR(255) NOT NULL, " +
                    "mapping_type VARCHAR(10) NOT NULL DEFAULT 'input', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (item_name, pagina_resource, mapping_type)" +
                    ")";
                adapter.executeUpdate(createSql);
                adapter.executeUpdate("CREATE INDEX idx_craft_recipes_item_type ON craft_recipes (item_name, mapping_type)");
                System.out.println("Recreated craft_recipes table with mapping_type column");
            }
        });

        return migrations;
    }

    private void ensureSqliteUniqueConstraints(DatabaseAdapter adapter) throws SQLException {
        boolean needsIngredientsMigration = checkNeedsIngredientsMigration(adapter);
        boolean needsFepsMigration = checkNeedsFepsMigration(adapter);

        if (needsIngredientsMigration) {
            recreateIngredientsTableWithConstraint(adapter);
        } else {
            System.out.println("UNIQUE constraint on ingredients already exists");
        }

        if (needsFepsMigration) {
            recreateFepsTableWithConstraint(adapter);
        } else {
            System.out.println("UNIQUE constraint on feps already exists");
        }
    }

    private boolean checkNeedsIngredientsMigration(DatabaseAdapter adapter) throws SQLException {
        try {
            adapter.executeUpdate("INSERT INTO ingredients (recipe_hash, name, percentage) VALUES ('__test__', '__test__', 0)");
            adapter.executeUpdate("INSERT INTO ingredients (recipe_hash, name, percentage) VALUES ('__test__', '__test__', 0)");
            adapter.executeUpdate("DELETE FROM ingredients WHERE recipe_hash = '__test__'");
            return true;
        } catch (SQLException e) {
            adapter.executeUpdate("DELETE FROM ingredients WHERE recipe_hash = '__test__'");
            return false;
        }
    }

    private boolean checkNeedsFepsMigration(DatabaseAdapter adapter) throws SQLException {
        try {
            adapter.executeUpdate("INSERT INTO feps (recipe_hash, name, value, weight) VALUES ('__test__', '__test__', 0, 0)");
            adapter.executeUpdate("INSERT INTO feps (recipe_hash, name, value, weight) VALUES ('__test__', '__test__', 0, 0)");
            adapter.executeUpdate("DELETE FROM feps WHERE recipe_hash = '__test__'");
            return true;
        } catch (SQLException e) {
            adapter.executeUpdate("DELETE FROM feps WHERE recipe_hash = '__test__'");
            return false;
        }
    }

    private void recreateIngredientsTableWithConstraint(DatabaseAdapter adapter) throws SQLException {
        adapter.executeUpdate("CREATE TABLE ingredients_new (" +
                             "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                             "recipe_hash VARCHAR(64) REFERENCES recipes (recipe_hash) ON DELETE CASCADE, " +
                             "name VARCHAR(255) NOT NULL, " +
                             "percentage FLOAT NOT NULL, " +
                             "resource_name VARCHAR(512), " +
                             "UNIQUE (recipe_hash, name))");

        adapter.executeUpdate("INSERT INTO ingredients_new (recipe_hash, name, percentage, resource_name) " +
                             "SELECT recipe_hash, name, MIN(percentage), resource_name FROM ingredients " +
                             "GROUP BY recipe_hash, name");

        adapter.executeUpdate("DROP TABLE ingredients");
        adapter.executeUpdate("ALTER TABLE ingredients_new RENAME TO ingredients");
        System.out.println("Added UNIQUE constraint to ingredients table");
    }

    private void recreateFepsTableWithConstraint(DatabaseAdapter adapter) throws SQLException {
        adapter.executeUpdate("CREATE TABLE feps_new (" +
                             "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                             "recipe_hash VARCHAR(64) REFERENCES recipes (recipe_hash) ON DELETE CASCADE, " +
                             "name VARCHAR(255) NOT NULL, " +
                             "value FLOAT NOT NULL, " +
                             "weight FLOAT NOT NULL, " +
                             "UNIQUE (recipe_hash, name))");

        adapter.executeUpdate("INSERT INTO feps_new (recipe_hash, name, value, weight) " +
                             "SELECT recipe_hash, name, MAX(value), MAX(weight) FROM feps " +
                             "GROUP BY recipe_hash, name");

        adapter.executeUpdate("DROP TABLE feps");
        adapter.executeUpdate("ALTER TABLE feps_new RENAME TO feps");
        System.out.println("Added UNIQUE constraint to feps table");
    }

    public abstract static class Migration {
        final int version;
        final String description;

        Migration(int version, String description) {
            this.version = version;
            this.description = description;
        }

        abstract void run(DatabaseAdapter adapter) throws SQLException;
    }
}
