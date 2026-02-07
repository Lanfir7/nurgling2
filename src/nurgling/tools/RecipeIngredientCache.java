package nurgling.tools;

import nurgling.NCore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache mapping item names to recipe pagina resources.
 * Two separate caches:
 *   - inputCache: item is used AS INGREDIENT in a recipe (for Alt+RMB: "what can I craft with this?")
 *   - outputCache: item is PRODUCED BY a recipe (for Shift+Click on input: "how do I make this?")
 *
 * Backed by the craft_recipes database table for persistence across sessions.
 * The in-memory cache is loaded from DB on startup and updated on recipe open.
 */
public class RecipeIngredientCache {

    public static class RecipeEntry {
        public final String paginaResource;
        public final String recipeName;

        public RecipeEntry(String paginaResource, String recipeName) {
            this.paginaResource = paginaResource;
            this.recipeName = recipeName;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(!(o instanceof RecipeEntry)) return false;
            RecipeEntry other = (RecipeEntry) o;
            return Objects.equals(paginaResource, other.paginaResource);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(paginaResource);
        }
    }

    /** Mapping type constants matching DB column values */
    public static final String TYPE_INPUT = "input";
    public static final String TYPE_OUTPUT = "output";

    /** item used AS INGREDIENT -> recipes that consume it */
    private static final Map<String, Set<RecipeEntry>> inputCache = new ConcurrentHashMap<>();
    /** item PRODUCED BY recipe -> recipes that produce it */
    private static final Map<String, Set<RecipeEntry>> outputCache = new ConcurrentHashMap<>();

    private static volatile boolean dbLoaded = false;

    /**
     * Add a mapping to the input cache (item is used as ingredient).
     */
    public static void addInputMapping(String itemName, String paginaResource, String recipeName) {
        if(itemName == null || paginaResource == null) return;
        inputCache.computeIfAbsent(itemName, k -> ConcurrentHashMap.newKeySet())
            .add(new RecipeEntry(paginaResource, recipeName));
    }

    /**
     * Add a mapping to the output cache (item is produced by recipe).
     */
    public static void addOutputMapping(String itemName, String paginaResource, String recipeName) {
        if(itemName == null || paginaResource == null) return;
        outputCache.computeIfAbsent(itemName, k -> ConcurrentHashMap.newKeySet())
            .add(new RecipeEntry(paginaResource, recipeName));
    }

    /**
     * Add input mappings for all ingredients of a recipe and persist to database.
     * Called from NMakewindow for recipe INPUTS.
     * If an ingredient name is a VSpec category (group), also caches all
     * specific items from that group for direct lookup.
     */
    public static void addInputsAndPersist(List<String> ingredientNames, String paginaResource, String recipeName) {
        if(paginaResource == null || recipeName == null) return;
        List<String> expandedNames = expandVSpecNames(ingredientNames);
        for(String name : expandedNames) {
            addInputMapping(name, paginaResource, recipeName);
        }
        persistAsync(expandedNames, paginaResource, recipeName, TYPE_INPUT);
    }

    /**
     * Add output mappings for all products of a recipe and persist to database.
     * Called from NMakewindow for recipe OUTPUTS.
     * If an output name is a VSpec category (group), also caches all
     * specific items from that group for direct lookup.
     */
    public static void addOutputsAndPersist(List<String> outputNames, String paginaResource, String recipeName) {
        if(paginaResource == null || recipeName == null) return;
        List<String> expandedNames = expandVSpecNames(outputNames);
        for(String name : expandedNames) {
            addOutputMapping(name, paginaResource, recipeName);
        }
        persistAsync(expandedNames, paginaResource, recipeName, TYPE_OUTPUT);
    }

    /**
     * Find recipes where the item is used AS INGREDIENT (for Alt+RMB).
     */
    public static Set<RecipeEntry> findInputRecipes(List<String> names) {
        Set<RecipeEntry> result = new HashSet<>();
        for(String name : names) {
            result.addAll(inputCache.getOrDefault(name, Collections.emptySet()));
        }
        return result;
    }

    /**
     * Find recipes that PRODUCE the item (for Shift+Click: "how to make this").
     */
    public static Set<RecipeEntry> findOutputRecipes(List<String> names) {
        Set<RecipeEntry> result = new HashSet<>();
        for(String name : names) {
            result.addAll(outputCache.getOrDefault(name, Collections.emptySet()));
        }
        return result;
    }

    /**
     * Load all mappings from the database into the in-memory cache.
     * Called once when the database becomes ready.
     */
    public static void loadFromDatabase() {
        if(dbLoaded) return;
        if(NCore.databaseManager != null && NCore.databaseManager.isReady()
                && NCore.databaseManager.getCraftRecipeService() != null) {
            NCore.databaseManager.getCraftRecipeService().loadAllIntoCacheAsync()
                .thenRun(() -> {
                    dbLoaded = true;
                    System.out.println("RecipeIngredientCache: loaded from database");
                })
                .exceptionally(ex -> {
                    System.err.println("RecipeIngredientCache: failed to load from DB: " + ex.getMessage());
                    return null;
                });
        }
    }

    /**
     * Check if the DB cache has been loaded.
     */
    public static boolean isDbLoaded() {
        return dbLoaded;
    }

    /**
     * Clear the cache (e.g., on session end).
     */
    public static void clear() {
        inputCache.clear();
        outputCache.clear();
        dbLoaded = false;
    }

    // --- internal helpers ---

    private static List<String> expandVSpecNames(List<String> names) {
        List<String> expanded = new ArrayList<>(names);
        for(String name : names) {
            if(VSpec.categories.containsKey(name)) {
                try {
                    ArrayList<String> members = VSpec.getCategoryContent(name);
                    for(String member : members) {
                        if(!expanded.contains(member)) {
                            expanded.add(member);
                        }
                    }
                } catch(Exception e) {
                    // VSpec lookup failed, proceed with original names
                }
            }
        }
        return expanded;
    }

    private static void persistAsync(List<String> names, String paginaResource, String recipeName, String mappingType) {
        if(NCore.databaseManager != null && NCore.databaseManager.isReady()
                && NCore.databaseManager.getCraftRecipeService() != null) {
            NCore.databaseManager.getCraftRecipeService()
                .saveMappingsAsync(names, paginaResource, recipeName, mappingType);
        }
    }
}
