package nurgling.db.dao;

import nurgling.db.DatabaseAdapter;
import nurgling.tools.RecipeIngredientCache;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * DAO for craft_recipes table.
 * Stores item_name -> (pagina_resource, recipe_name, mapping_type) mappings
 * shared across all characters and sessions.
 *
 * mapping_type = 'input'  — item is used as ingredient in the recipe
 * mapping_type = 'output' — item is produced by the recipe
 */
public class CraftRecipeDao {

    /**
     * Save a single mapping (upsert).
     */
    public void saveMapping(DatabaseAdapter adapter, String itemName,
                            String paginaResource, String recipeName,
                            String mappingType) throws SQLException {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("item_name", itemName);
        data.put("pagina_resource", paginaResource);
        data.put("recipe_name", recipeName);
        data.put("mapping_type", mappingType);

        String sql = adapter.getUpsertSql("craft_recipes", data,
                List.of("item_name", "pagina_resource", "mapping_type"));
        adapter.executeUpdate(sql, itemName, paginaResource, recipeName, mappingType);
    }

    /**
     * Save multiple item mappings for one recipe in a batch.
     */
    public void saveMappings(DatabaseAdapter adapter, List<String> itemNames,
                             String paginaResource, String recipeName,
                             String mappingType) throws SQLException {
        for(String itemName : itemNames) {
            saveMapping(adapter, itemName, paginaResource, recipeName, mappingType);
        }
    }

    /**
     * Find all recipes by item names and mapping type.
     * For TYPE_INPUT: "where is this item used as ingredient?"
     * For TYPE_OUTPUT: "what recipes produce this item?"
     */
    public Set<RecipeIngredientCache.RecipeEntry> findByItemNames(
            DatabaseAdapter adapter, List<String> itemNames, String mappingType) throws SQLException {
        if(itemNames.isEmpty()) return Collections.emptySet();

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT DISTINCT pagina_resource, recipe_name FROM craft_recipes WHERE mapping_type = ? AND item_name IN (");
        for(int i = 0; i < itemNames.size(); i++) {
            if(i > 0) sb.append(",");
            sb.append("?");
        }
        sb.append(")");

        Object[] params = new Object[1 + itemNames.size()];
        params[0] = mappingType;
        for(int i = 0; i < itemNames.size(); i++) {
            params[i + 1] = itemNames.get(i);
        }

        Set<RecipeIngredientCache.RecipeEntry> results = new HashSet<>();
        try(ResultSet rs = adapter.executeQuery(sb.toString(), params)) {
            while(rs.next()) {
                results.add(new RecipeIngredientCache.RecipeEntry(
                    rs.getString("pagina_resource"),
                    rs.getString("recipe_name")
                ));
            }
        }
        return results;
    }

    /**
     * Load all mappings from the table, grouped by item name and type.
     * Returns a map: mapping_type -> (item_name -> Set&lt;RecipeEntry&gt;)
     */
    public Map<String, Map<String, Set<RecipeIngredientCache.RecipeEntry>>> loadAll(
            DatabaseAdapter adapter) throws SQLException {
        Map<String, Map<String, Set<RecipeIngredientCache.RecipeEntry>>> result = new HashMap<>();
        result.put(RecipeIngredientCache.TYPE_INPUT, new HashMap<>());
        result.put(RecipeIngredientCache.TYPE_OUTPUT, new HashMap<>());

        try(ResultSet rs = adapter.executeQuery(
                "SELECT item_name, pagina_resource, recipe_name, mapping_type FROM craft_recipes")) {
            while(rs.next()) {
                String itemName = rs.getString("item_name");
                String type = rs.getString("mapping_type");
                if(type == null) type = RecipeIngredientCache.TYPE_INPUT; // legacy rows
                Map<String, Set<RecipeIngredientCache.RecipeEntry>> typeMap =
                    result.computeIfAbsent(type, k -> new HashMap<>());
                typeMap.computeIfAbsent(itemName, k -> new HashSet<>())
                    .add(new RecipeIngredientCache.RecipeEntry(
                        rs.getString("pagina_resource"),
                        rs.getString("recipe_name")
                    ));
            }
        }
        return result;
    }
}
