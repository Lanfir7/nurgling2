package nurgling.db.service;

import nurgling.db.DatabaseManager;
import nurgling.db.dao.CraftRecipeDao;
import nurgling.tools.RecipeIngredientCache;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service layer for craft recipe ingredient-to-recipe mappings.
 * Shared across all characters and sessions.
 *
 * Supports two mapping types:
 *   - "input"  — item is used as ingredient
 *   - "output" — item is produced by recipe
 */
public class CraftRecipeService {
    private final DatabaseManager databaseManager;
    private final CraftRecipeDao craftRecipeDao;

    public CraftRecipeService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.craftRecipeDao = new CraftRecipeDao();
    }

    /**
     * Save mappings asynchronously with mapping type.
     */
    public CompletableFuture<Void> saveMappingsAsync(List<String> itemNames,
                                                      String paginaResource, String recipeName,
                                                      String mappingType) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveMappings(itemNames, paginaResource, recipeName, mappingType);
            } catch (SQLException e) {
                System.err.println("Failed to save craft recipe mappings: " + e.getMessage());
            }
        });
    }

    /**
     * Save mappings synchronously with mapping type.
     */
    public void saveMappings(List<String> itemNames,
                             String paginaResource, String recipeName,
                             String mappingType) throws SQLException {
        databaseManager.executeOperation(adapter -> {
            craftRecipeDao.saveMappings(adapter, itemNames, paginaResource, recipeName, mappingType);
            return null;
        });
    }

    /**
     * Find recipes where items are used as ingredients (input type).
     */
    public Set<RecipeIngredientCache.RecipeEntry> findByIngredients(
            List<String> itemNames) throws SQLException {
        return databaseManager.executeOperation(adapter ->
            craftRecipeDao.findByItemNames(adapter, itemNames, RecipeIngredientCache.TYPE_INPUT));
    }

    /**
     * Find recipes that produce items (output type).
     */
    public Set<RecipeIngredientCache.RecipeEntry> findByProducts(
            List<String> itemNames) throws SQLException {
        return databaseManager.executeOperation(adapter ->
            craftRecipeDao.findByItemNames(adapter, itemNames, RecipeIngredientCache.TYPE_OUTPUT));
    }

    /**
     * Load all mappings into the in-memory cache asynchronously.
     */
    public CompletableFuture<Void> loadAllIntoCacheAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                loadAllIntoCache();
            } catch (SQLException e) {
                System.err.println("Failed to load craft recipes into cache: " + e.getMessage());
            }
        });
    }

    /**
     * Load all mappings into the in-memory cache synchronously.
     */
    public void loadAllIntoCache() throws SQLException {
        Map<String, Map<String, Set<RecipeIngredientCache.RecipeEntry>>> all =
            databaseManager.executeOperation(adapter -> craftRecipeDao.loadAll(adapter));

        // Load input mappings
        Map<String, Set<RecipeIngredientCache.RecipeEntry>> inputs =
            all.getOrDefault(RecipeIngredientCache.TYPE_INPUT, Collections.emptyMap());
        for(Map.Entry<String, Set<RecipeIngredientCache.RecipeEntry>> entry : inputs.entrySet()) {
            for(RecipeIngredientCache.RecipeEntry re : entry.getValue()) {
                RecipeIngredientCache.addInputMapping(entry.getKey(), re.paginaResource, re.recipeName);
            }
        }

        // Load output mappings
        Map<String, Set<RecipeIngredientCache.RecipeEntry>> outputs =
            all.getOrDefault(RecipeIngredientCache.TYPE_OUTPUT, Collections.emptyMap());
        for(Map.Entry<String, Set<RecipeIngredientCache.RecipeEntry>> entry : outputs.entrySet()) {
            for(RecipeIngredientCache.RecipeEntry re : entry.getValue()) {
                RecipeIngredientCache.addOutputMapping(entry.getKey(), re.paginaResource, re.recipeName);
            }
        }

        // Load spec mappings (original ingredient name:count per recipe)
        Map<String, Set<RecipeIngredientCache.RecipeEntry>> specs =
            all.getOrDefault(RecipeIngredientCache.TYPE_SPEC, Collections.emptyMap());
        Map<String, List<RecipeIngredientCache.IngredientSpec>> specsByRecipe = new HashMap<>();
        for(Map.Entry<String, Set<RecipeIngredientCache.RecipeEntry>> entry : specs.entrySet()) {
            String encoded = entry.getKey(); // "Name:Count"
            int colonIdx = encoded.lastIndexOf(':');
            if(colonIdx <= 0) continue;
            String name = encoded.substring(0, colonIdx);
            int count;
            try {
                count = Integer.parseInt(encoded.substring(colonIdx + 1));
            } catch(NumberFormatException e) { continue; }
            for(RecipeIngredientCache.RecipeEntry re : entry.getValue()) {
                specsByRecipe.computeIfAbsent(re.paginaResource, k -> new ArrayList<>())
                    .add(new RecipeIngredientCache.IngredientSpec(name, count));
            }
        }
        for(Map.Entry<String, List<RecipeIngredientCache.IngredientSpec>> entry : specsByRecipe.entrySet()) {
            RecipeIngredientCache.setRecipeSpecsFromDB(entry.getKey(), entry.getValue());
        }
    }
}
