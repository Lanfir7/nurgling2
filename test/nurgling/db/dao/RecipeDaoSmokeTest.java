package nurgling.db.dao;

import nurgling.cookbook.Recipe;
import nurgling.db.SqliteAdapter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeDaoSmokeTest {
    @Test
    void smokeResourceRoundTripsWithoutSchemaChange() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacyTables(connection);
            RecipeDao dao = new RecipeDao();
            SqliteAdapter adapter = new SqliteAdapter(connection);

            Map<String, Recipe.IngredientInfo> ings = new HashMap<String, Recipe.IngredientInfo>();
            ings.put("Pork", new Recipe.IngredientInfo(70.0, "gfx/invobjs/meat"));
            Map<String, Double> woods = new HashMap<String, Double>();
            woods.put("Willow", Double.valueOf(40.0));
            woods.put("Oak", Double.valueOf(60.0));
            Map<String, Recipe.Fep> feps = new HashMap<String, Recipe.Fep>();
            feps.put("Strength +1", new Recipe.Fep(1.2, 1.0));
            Recipe saved = new Recipe("h-sm", "Smoked Pork", "gfx/food", 2.0, 20, ings, woods, feps);

            dao.saveRecipe(adapter, saved);

            assertEquals(Recipe.SMOKE_RESOURCE, resourceName(connection, "h-sm", "Oak"));
            assertEquals(Recipe.SMOKE_RESOURCE, resourceName(connection, "h-sm", "Willow"));
            assertEquals("gfx/invobjs/meat", resourceName(connection, "h-sm", "Pork"));
            assertEquals(3, count(connection, "ingredients"));

            List<Recipe> loaded = dao.loadRecipes(adapter, Collections.singletonList("h-sm"));
            assertEquals(1, loaded.size());
            Recipe recipe = loaded.get(0);
            assertTrue(recipe.getIngredients().containsKey("Pork"));
            assertFalse(recipe.getIngredients().containsKey("Oak"));
            assertFalse(recipe.getIngredients().containsKey("Willow"));
            assertEquals(60.0, recipe.getSmokingWoods().get("Oak"), 0.0);
            assertEquals(40.0, recipe.getSmokingWoods().get("Willow"), 0.0);

            List<Recipe> all = dao.loadAllRecipes(adapter);
            assertEquals(1, all.size());
            assertEquals(60.0, all.get(0).getSmokingWoods().get("Oak"), 0.0);
        }
    }

    @Test
    void collidingWoodNameDoesNotCorruptIngredient() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacyTables(connection);
            RecipeDao dao = new RecipeDao();
            SqliteAdapter adapter = new SqliteAdapter(connection);

            Map<String, Recipe.IngredientInfo> ings = new HashMap<String, Recipe.IngredientInfo>();
            ings.put("Oak", new Recipe.IngredientInfo(80.0, "gfx/invobjs/oak"));
            Map<String, Double> woods = new HashMap<String, Double>();
            woods.put("Oak", Double.valueOf(100.0));
            Recipe saved = new Recipe("h-col", "Oak Dish", "gfx/food", 1.0, 10, ings, woods,
                    new HashMap<String, Recipe.Fep>());

            dao.saveRecipe(adapter, saved);

            assertEquals("gfx/invobjs/oak", resourceName(connection, "h-col", "Oak"));
            assertEquals(1, count(connection, "ingredients"));

            Recipe loaded = dao.loadRecipes(adapter, Collections.singletonList("h-col")).get(0);
            assertEquals(80.0, loaded.getIngredients().get("Oak").percentage, 0.0);
            assertTrue(loaded.getSmokingWoods().isEmpty());
        }
    }

    @Test
    void woodsOnlyRecipeDoesNotWipeSmokeRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacyTables(connection);
            RecipeDao dao = new RecipeDao();
            SqliteAdapter adapter = new SqliteAdapter(connection);

            Map<String, Double> woods = new HashMap<String, Double>();
            woods.put("Birch", Double.valueOf(100.0));
            Recipe saved = new Recipe("h-wood", "Smoked", "gfx/food", 1.0, 10,
                    new HashMap<String, Recipe.IngredientInfo>(), woods,
                    new HashMap<String, Recipe.Fep>());

            dao.saveRecipe(adapter, saved);
            assertEquals(Recipe.SMOKE_RESOURCE, resourceName(connection, "h-wood", "Birch"));

            Recipe loaded = dao.loadAllRecipes(adapter).get(0);
            assertTrue(loaded.getIngredients().isEmpty());
            assertEquals(100.0, loaded.getSmokingWoods().get("Birch"), 0.0);
        }
    }

    @Test
    void legacyWoodStoredAsIngredientStaysIngredient() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacyTables(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO recipes VALUES ('h-old', 'Old Smoke', 'gfx/food', 1, 10)");
                statement.execute("INSERT INTO ingredients (recipe_hash, name, percentage, resource_name) " +
                        "VALUES ('h-old', 'Oak', 100, NULL)");
            }
            Recipe loaded = new RecipeDao().loadRecipes(new SqliteAdapter(connection),
                    Collections.singletonList("h-old")).get(0);
            assertTrue(loaded.getIngredients().containsKey("Oak"));
            assertTrue(loaded.getSmokingWoods().isEmpty());
            assertNull(loaded.getIngredients().get("Oak").resourceName);
        }
    }

    private static void createLegacyTables(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE recipes (" +
                    "recipe_hash VARCHAR(64) PRIMARY KEY, " +
                    "item_name VARCHAR(255) NOT NULL, " +
                    "resource_name VARCHAR(255) NOT NULL, " +
                    "hunger FLOAT NOT NULL, " +
                    "energy INT NOT NULL)");
            statement.execute("CREATE TABLE ingredients (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "recipe_hash VARCHAR(64) REFERENCES recipes (recipe_hash) ON DELETE CASCADE, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "percentage FLOAT NOT NULL, " +
                    "resource_name VARCHAR(512), " +
                    "UNIQUE (recipe_hash, name))");
            statement.execute("CREATE TABLE feps (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "recipe_hash VARCHAR(64) REFERENCES recipes (recipe_hash) ON DELETE CASCADE, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "value FLOAT NOT NULL, " +
                    "weight FLOAT NOT NULL, " +
                    "UNIQUE (recipe_hash, name))");
        }
    }

    private static String resourceName(Connection connection, String hash, String name) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT resource_name FROM ingredients WHERE recipe_hash = '" + hash +
                             "' AND name = '" + name + "'")) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private static int count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getInt(1) : -1;
        }
    }
}
