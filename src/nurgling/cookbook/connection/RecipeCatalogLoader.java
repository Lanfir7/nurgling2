package nurgling.cookbook.connection;

import nurgling.cookbook.GenericTypes;
import nurgling.cookbook.Recipe;
import nurgling.cookbook.Stock;
import nurgling.cookbook.Variations;
import nurgling.craft.WikiRecipes;
import nurgling.db.DatabaseManager;
import nurgling.db.dao.StorageItemDao;
import nurgling.widgets.storage.VillageStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

        default List<StorageItemDao.StorageItemData> loadStorageItems() throws Exception { return null; }
    }

    public interface TaskSubmitter {
        Future<?> submit(Runnable task);
    }

    public final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile List<Recipe> recipes = Collections.emptyList();
    private volatile String error = null;
    private final CatalogSource source;
    private volatile Variations variations = null;
    private final VillageStorage storage;
    /** Storage setting captured with the village snapshot on the UI thread. */
    public final boolean onlyVillage;
    /** Whether the village could be worked out when this load started. */
    public final boolean villageKnowable;
    private volatile Stock stock = null;
    private volatile Set<String> cookable = Collections.emptySet();

    public RecipeCatalogLoader(DatabaseManager databaseManager) {
        this(new DatabaseCatalogSource(databaseManager), null);
    }

    public RecipeCatalogLoader(DatabaseManager databaseManager, VillageStorage storage) {
        this(new DatabaseCatalogSource(databaseManager), storage);
    }

    public RecipeCatalogLoader(CatalogSource source) {
        this(source, null);
    }

    public RecipeCatalogLoader(CatalogSource source, VillageStorage storage) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        this.source = source;
        this.storage = storage;
        this.onlyVillage = VillageStorage.onlyVillage();
        this.villageKnowable = storage == null || storage.knowable();
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
            try {
                if (error == null) {
                    variations = variations(recipes);
                    stock = loadStock();
                    cookable = cookable(recipes, variations, stock != null ? stock.keys() : Collections.emptySet());
                }
            } catch (RuntimeException e) {
                System.err.println("[Cookbook] Optional recipe details unavailable: " + e.getMessage());
            } finally {
                ready.set(true);
            }
        }
    }

    /** Loaded recipes with {@link Recipe#isFavorite()} applied; empty until {@link #ready}. */
    public List<Recipe> recipes() {
        return recipes;
    }

    /** The untried variations of the loaded recipes; null until {@link #ready} and when the offline data is missing. */
    public Variations variations() {
        return variations;
    }

    /**
     * The items in storage, limited to the village exactly as the Storage Items window does; null
     * until {@link #ready} and when storage could not be read.
     */
    public Stock stock() {
        return stock;
    }

    /** Hashes of the recipes whose every listed ingredient is in the village's storage; empty until {@link #ready}. */
    public Set<String> cookable() {
        return cookable;
    }

    private static Set<String> cookable(List<Recipe> recipes, Variations variations, Set<String> stored) {
        Set<String> ret = new HashSet<>();
        if (variations != null) {
            for (Recipe r : recipes)
                if (variations.cookable(r, stored))
                    ret.add(r.getHash());
        }
        return ret;
    }

    private Stock loadStock() {
        try {
            List<StorageItemDao.StorageItemData> loaded = source.loadStorageItems();
            if (loaded == null)
                return null;
            List<StorageItemDao.StorageItemData> items = VillageStorage.valid(loaded);
            int all = items.size();
            if (storage != null && onlyVillage) {
                Set<Long> village = storage.village();
                items = VillageStorage.inVillage(items, village);
                System.out.println("[Cookbook] Storage: " + items.size() + " of " + all + " items in the village (" + village.size() + " chunks"
                        + (village.isEmpty() ? ", none found, so all count" : "") + ")");
            }
            return new Stock(items);
        } catch (Exception e) {
            /* The recipes loaded; only the storage frames and counts are missing. */
            System.err.println("[Cookbook] Failed to load storage items: " + e.getMessage());
            return null;
        }
    }

    private static Variations variations(List<Recipe> recipes) {
        try {
            return new Variations(recipes, WikiRecipes.bundled(), GenericTypes.bundled());
        } catch (IOException | RuntimeException e) {
            System.err.println("[Cookbook] Untried variations unavailable: " + e.getMessage());
            return null;
        }
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

        @Override
        public List<StorageItemDao.StorageItemData> loadStorageItems() throws Exception {
            return databaseManager.executeOperation(adapter -> new StorageItemDao().loadAllStorageItems(adapter));
        }
    }
}
