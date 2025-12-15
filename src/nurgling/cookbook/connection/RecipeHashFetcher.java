package nurgling.cookbook.connection;

import nurgling.DBPoolManager;
import nurgling.NConfig;
import nurgling.cookbook.Recipe;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecipeHashFetcher implements Runnable {
    private final DBPoolManager poolManager;
    private ArrayList<Recipe> recipes;
    public AtomicBoolean ready = new AtomicBoolean(false);
    private String sql;

    public RecipeHashFetcher(DBPoolManager poolManager, String sql) {
        this.poolManager = poolManager;
        this.recipes = new ArrayList<>();
        this.sql = sql;
    }

    public void run() {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            // Проверяем прерывание перед началом
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("RecipeHashFetcher: Task was cancelled before starting");
                ready.set(true);
                return;
            }
            
            System.out.println("RecipeHashFetcher: Starting to fetch recipes from database");
            
            conn = poolManager.getConnection();
            if (conn == null) {
                System.err.println("RecipeHashFetcher: Unable to get database connection");
                return;
            }
            String query;
            if ((Boolean) NConfig.get(NConfig.Key.postgres)) {
                query = "SELECT " +
                        "r.recipe_hash, r.item_name, r.resource_name, r.hunger, r.energy, " +
                        "f.name as fep_name, f.value as fep_value, f.weight as fep_weight, " +
                        "i.name as ing_name, i.percentage as ing_percentage, i.resource_name as ing_resource, " +
                        "CASE WHEN fav.recipe_hash IS NOT NULL THEN TRUE ELSE FALSE END as is_favorite " +
                        "FROM recipes r " +
                        "LEFT JOIN feps f ON r.recipe_hash = f.recipe_hash " +
                        "LEFT JOIN ingredients i ON r.recipe_hash = i.recipe_hash " +
                        "LEFT JOIN favorite_recipes fav ON r.recipe_hash = fav.recipe_hash " +
                        "WHERE " + extractWhereClause(sql);
            } else { // SQLite
                query = "SELECT " +
                        "r.recipe_hash, r.item_name, r.resource_name, r.hunger, r.energy, " +
                        "f.name as fep_name, f.value as fep_value, f.weight as fep_weight, " +
                        "i.name as ing_name, i.percentage as ing_percentage, i.resource_name as ing_resource, " +
                        "CASE WHEN fav.recipe_hash IS NOT NULL THEN 1 ELSE 0 END as is_favorite " +
                        "FROM recipes r " +
                        "LEFT JOIN feps f ON r.recipe_hash = f.recipe_hash " +
                        "LEFT JOIN ingredients i ON r.recipe_hash = i.recipe_hash " +
                        "LEFT JOIN favorite_recipes fav ON r.recipe_hash = fav.recipe_hash " +
                        "WHERE " + extractWhereClause(sql);
            }

            System.out.println("RecipeHashFetcher: Executing query: " + query.substring(0, Math.min(100, query.length())) + "...");
            
            // Проверяем, не закрыто ли соединение
            if (conn.isClosed()) {
                System.err.println("RecipeHashFetcher: Connection is closed!");
                ready.set(true);
                return;
            }
            
            stmt = conn.createStatement();
            // Устанавливаем таймаут для запроса (10 секунд вместо 30)
            stmt.setQueryTimeout(10);
            long startTime = System.currentTimeMillis();
            ResultSet resultSet = stmt.executeQuery(query);
            rs = resultSet;
            long queryTime = System.currentTimeMillis() - startTime;
            System.out.println("RecipeHashFetcher: Query executed in " + queryTime + "ms");

            Map<String, Recipe> recipeMap = new HashMap<>();
            int rowCount = 0;

            while (rs.next()) {
                // Проверяем прерывание в цикле обработки результатов
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("RecipeHashFetcher: Task was cancelled during result processing");
                    break;
                }
                
                rowCount++;
                String hash = rs.getString("recipe_hash");
                
                // Извлекаем значения из ResultSet перед лямбда-выражением (должны быть effectively final)
                final String itemName = rs.getString("item_name");
                final String resourceName = rs.getString("resource_name");
                final double hunger = rs.getDouble("hunger");
                final int energy = rs.getInt("energy");
                final boolean isFavorite = rs.getBoolean("is_favorite");

                Recipe recipe = recipeMap.computeIfAbsent(hash, k -> {
                    Recipe r = new Recipe(
                            hash,
                            itemName,
                            resourceName,
                            hunger,
                            energy,
                            new HashMap<>(), // Ingredients
                            new HashMap<>()   // FEPS
                    );
                    r.setFavorite(isFavorite);
                    return r;
                });

                // Добавляем FEP если есть
                String fepName = rs.getString("fep_name");
                if (fepName != null && !recipe.getFeps().containsKey(fepName)) {
                    recipe.getFeps().put(fepName,
                            new Recipe.Fep(
                                    rs.getDouble("fep_value"),
                                    rs.getDouble("fep_weight")
                            ));
                }

                // Добавляем ингредиент если есть
                String ingName = rs.getString("ing_name");
                if (ingName != null && !recipe.getIngredients().containsKey(ingName)) {
                    String ingResource = rs.getString("ing_resource");
                    recipe.getIngredients().put(
                            ingName,
                            new Recipe.IngredientInfo(rs.getDouble("ing_percentage"), ingResource)
                    );
                }
            }
            
            recipes = new ArrayList<>(recipeMap.values());
            System.out.println("RecipeHashFetcher: Successfully fetched " + recipes.size() + " recipes from database (processed " + rowCount + " rows)");
            conn.commit();
        } catch (SQLException e) {
            // Проверяем, не было ли прерывания
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("RecipeHashFetcher: Task was cancelled (SQLException during interrupt)");
            } else {
                System.err.println("RecipeHashFetcher: SQLException fetching recipes: " + e.getMessage());
                System.err.println("RecipeHashFetcher: SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode());
                e.printStackTrace();
            }
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignore) {
                }
            }
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("RecipeHashFetcher: Task was cancelled (Exception during interrupt)");
            } else {
                System.err.println("RecipeHashFetcher: Unexpected error: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            // Закрываем ресурсы
            try {
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException e) {
                System.err.println("RecipeHashFetcher: Error closing ResultSet: " + e.getMessage());
            }
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch (SQLException e) {
                System.err.println("RecipeHashFetcher: Error closing Statement: " + e.getMessage());
            }
            ready.set(true);
            System.out.println("RecipeHashFetcher: Marked as ready");
            if (conn != null) {
                poolManager.returnConnection(conn);
            }
        }
    }

    private String extractWhereClause(String inputSql) {
        if ((Boolean) NConfig.get(NConfig.Key.sqlite)) {
            inputSql = inputSql.replace("ILIKE", "LIKE");
        }

        if (inputSql.toLowerCase().contains("where") ||
                inputSql.toLowerCase().contains("join") ||
                inputSql.toLowerCase().contains("order by")) {
            return ((Boolean) NConfig.get(NConfig.Key.sqlite)) ? extractWhereFromSql(inputSql).replace("ILIKE", "LIKE") : extractWhereFromSql(inputSql);
        } else {
            return ((Boolean) NConfig.get(NConfig.Key.sqlite)) ? parseFilterSyntax(inputSql).replace("ILIKE", "LIKE") : parseFilterSyntax(inputSql);
        }
    }

    private String extractWhereFromSql(String sql) {
        String lowerSql = sql.toLowerCase();
        int wherePos = lowerSql.indexOf("where");
        if (wherePos >= 0) {
            int orderByPos = lowerSql.indexOf("order by");
            if (orderByPos > wherePos) {
                return sql.substring(wherePos + 5, orderByPos).trim();
            }
            return sql.substring(wherePos + 5).trim();
        }
        return "1=1";
    }

    private String parseFilterSyntax(String filterString) {
        if (filterString == null || filterString.trim().isEmpty()) {
            return "1=1";
        }

        List<String> conditions = new ArrayList<>();
        String[] parts = filterString.split(";");

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            try {
                if (part.startsWith("name:")) {
                    String value = part.substring(5).trim();
                    boolean exact = value.startsWith("\"") && value.endsWith("\"");
                    boolean exclude = part.startsWith("-name:");

                    if (exact) {
                        value = value.substring(1, value.length() - 1);
                        conditions.add(exclude ?
                                "LOWER(r.item_name) != LOWER('" + escapeSql(value) + "')" :
                                "LOWER(r.item_name) = LOWER('" + escapeSql(value) + "')");
                    } else {
                        conditions.add(exclude ?
                                "r.item_name NOT ILIKE '%" + escapeSql(value) + "%'" :
                                "r.item_name ILIKE '%" + escapeSql(value) + "%'");
                    }
                } else if (part.startsWith("from:") || part.startsWith("-from:")) {
                    boolean exclude = part.startsWith("-from:");
                    String value = part.substring(exclude ? 6 : 5).trim();
                    boolean exact = value.startsWith("\"") && value.endsWith("\"");

                    if (exact) {
                        value = value.substring(1, value.length() - 1);
                        conditions.add(exclude ?
                                "NOT EXISTS (SELECT 1 FROM ingredients i WHERE i.recipe_hash = r.recipe_hash AND LOWER(i.name) = LOWER('" + escapeSql(value) + "'))" :
                                "EXISTS (SELECT 1 FROM ingredients i WHERE i.recipe_hash = r.recipe_hash AND LOWER(i.name) = LOWER('" + escapeSql(value) + "'))");
                    } else {
                        conditions.add(exclude ?
                                "NOT EXISTS (SELECT 1 FROM ingredients i WHERE i.recipe_hash = r.recipe_hash AND i.name ILIKE '%" + escapeSql(value) + "%')" :
                                "EXISTS (SELECT 1 FROM ingredients i WHERE i.recipe_hash = r.recipe_hash AND i.name ILIKE '%" + escapeSql(value) + "%')");
                    }
                } else if (part.matches("(str|agi|con|int|dex|per|wil|psy|cha)(2?)\\s*([<>]=?|=)\\s*(\\d+)(%?)")) {
                    Matcher m = Pattern.compile("(str|agi|con|int|dex|per|wil|psy|cha)(2?)\\s*([<>]=?|=)\\s*(\\d+)(%?)").matcher(part);
                    if (m.find()) {
                        String fepType = mapFepType(m.group(1));
                        String fepLevel = m.group(2).isEmpty() ? "1" : "2";
                        String operator = m.group(3);
                        String value = m.group(4);
                        boolean isPercentage = !m.group(5).isEmpty();

                        String fepName = fepType + " +" + fepLevel;

                        if (isPercentage) {
                            conditions.add(String.format(
                                    "r.recipe_hash IN (" +
                                            "SELECT r2.recipe_hash FROM recipes r2 " +
                                            "JOIN feps f2 ON r2.recipe_hash = f2.recipe_hash " +
                                            "GROUP BY r2.recipe_hash " +
                                            "HAVING COALESCE(SUM(CASE WHEN f2.name = '%s' THEN f2.value ELSE 0 END), 0) / " +
                                            "NULLIF(SUM(f2.value), 0) * 100 %s %s" +
                                            ")", fepName, operator, value));
                        } else {
                            conditions.add(String.format(
                                    "EXISTS (SELECT 1 FROM feps f WHERE f.recipe_hash = r.recipe_hash AND f.name = '%s' AND f.value %s %s)",
                                    fepName, operator, value));
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing filter condition: " + part);
            }
        }

        return conditions.isEmpty() ? "1=1" : String.join(" AND ", conditions);
    }

    private String mapFepType(String shortType) {
        switch (shortType) {
            case "str":
                return "Strength";
            case "agi":
                return "Agility";
            case "con":
                return "Constitution";
            case "int":
                return "Intelligence";
            case "dex":
                return "Dexterity";
            case "per":
                return "Perception";
            case "wil":
                return "Will";
            case "psy":
                return "Psyche";
            case "cha":
                return "Charisma";
            default:
                return shortType;
        }
    }

    private String escapeSql(String input) {
        return input.replace("'", "''");
    }

    public static String genFep(String type, boolean desc) {
        if ((Boolean) NConfig.get(NConfig.Key.postgres)) {
            return "FROM recipes r " +
                    "LEFT JOIN feps f ON r.recipe_hash = f.recipe_hash AND f.name = '" + type + "' " +
                    "GROUP BY r.recipe_hash, f.name, r.resource_name, r.hunger, r.energy, f.value " +
                    "ORDER BY COALESCE(f.value, 0) " + (desc ? "DESC" : "ASC");
        } else { // SQLite
            return "FROM recipes r " +
                    "LEFT JOIN feps f ON r.recipe_hash = f.recipe_hash AND f.name = '" + type + "' " +
                    "GROUP BY r.recipe_hash, r.resource_name, r.hunger, r.energy " +
                    "ORDER BY IFNULL(f.value, 0) " + (desc ? "DESC" : "ASC");
        }
    }

    public ArrayList<Recipe> getRecipes() {
        return recipes;
    }
}
