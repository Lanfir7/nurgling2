package nurgling.cookbook.connection;

import nurgling.cookbook.Recipe;
import nurgling.db.DatabaseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads the full cookbook catalog off the UI thread.
 *
 * <p>Filters stay in memory. {@link CatalogSource} is the headless seam: production talks to
 * {@link DatabaseManager} / RecipeService / FavoriteRecipeService; tests inject recipes and
 * favorite hashes without a live database.
 */
public class RecipeCatalogLoader implements Runnable {
    public interface CatalogSource {
        boolean isReady();

        List<Recipe> loadRecipes() throws Exception;

        Set<String> loadFavoriteHashes() throws Exception;
    }

    public interface TaskSubmitter {
        Future<?> submit(Runnable task);
    }

    public final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile List<Recipe> recipes = Collections.emptyList();
    private volatile String error = null;
    private final CatalogSource source;

    public RecipeCatalogLoader(DatabaseManager databaseManager) {
        this(new DatabaseCatalogSource(databaseManager));
    }

    public RecipeCatalogLoader(CatalogSource source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        this.source = source;
    }

    /** Synchronous load for tests. Skips when the source is not ready. */
    public boolean start() {
        if (!source.isReady()) {
            return false;
        }
        run();
        return true;
    }

    /** Skip when the database is not ready; otherwise submit onto the DB worker pool. */
    public Future<?> enqueue(DatabaseManager databaseManager) {
        if (databaseManager == null || !databaseManager.isReady()) {
            return null;
        }
        return databaseManager.submitTask(this);
    }

    public Future<?> enqueue(TaskSubmitter submitter) {
        if (!source.isReady() || submitter == null) {
            return null;
        }
        return submitter.submit(this);
    }

    @Override
    public void run() {
        try {
            recipes = mapCatalog(source.loadRecipes(), source.loadFavoriteHashes());
            error = null;
        } catch (Exception e) {
            error = (e.getMessage() != null && !e.getMessage().isEmpty()) ? e.getMessage() : e.toString();
            recipes = Collections.emptyList();
            System.err.println("[Cookbook] Failed to load recipes: " + error);
        } finally {
            ready.set(true);
        }
    }

    /** Loaded recipes with {@link Recipe#isFavorite()} applied; empty until {@link #ready}. */
    public List<Recipe> recipes() {
        return recipes;
    }

    /** Why loading failed, or null. */
    public String error() {
        return error;
    }

    static List<Recipe> mapCatalog(List<Recipe> loaded, Set<String> favoriteHashes) {
        if (loaded == null || loaded.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> favs = favoriteHashes == null ? Collections.<String>emptySet() : favoriteHashes;
        List<Recipe> mapped = new ArrayList<Recipe>(loaded.size());
        for (Recipe recipe : loaded) {
            if (recipe == null) {
                continue;
            }
            String hash = recipe.getHash();
            Recipe mappedRecipe = new Recipe(
                    hash,
                    recipe.getName(),
                    recipe.getResourceName(),
                    recipe.getHunger(),
                    recipe.getEnergy(),
                    recipe.getIngredients(),
                    recipe.getSmokingWoods(),
                    recipe.getFeps()
            );
            mappedRecipe.setFavorite(hash != null && favs.contains(hash));
            mapped.add(mappedRecipe);
        }
        return Collections.unmodifiableList(mapped);
    }

    public static final class DatabaseCatalogSource implements CatalogSource {
        private final DatabaseManager databaseManager;

        public DatabaseCatalogSource(DatabaseManager databaseManager) {
            this.databaseManager = databaseManager;
        }

        @Override
        public boolean isReady() {
            return databaseManager != null && databaseManager.isReady();
        }

        @Override
        public List<Recipe> loadRecipes() throws Exception {
            return databaseManager.getRecipeService().loadAllRecipes();
        }

        @Override
        public Set<String> loadFavoriteHashes() throws Exception {
            return databaseManager.getFavoriteRecipeService().loadFavorites();
        }
    }
}
