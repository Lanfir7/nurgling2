package nurgling.cookbook;

import nurgling.cookbook.connection.RecipeCatalogLoader;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeCatalogLoaderTest {

    @Test
    void mapsFavoriteHashesOntoMatchingRecipes() {
        Recipe pie = recipe("h1", "Pie");
        Recipe stew = recipe("h2", "Stew");
        pie.setFavorite(true);
        FakeSource source = FakeSource.ready(Arrays.asList(pie, stew), setOf("h2", "missing"));

        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);
        assertTrue(loader.start());

        assertTrue(loader.ready.get());
        assertNull(loader.error());
        List<Recipe> rows = loader.recipes();
        assertEquals(2, rows.size());
        assertEquals("h1", rows.get(0).getHash());
        assertFalse(rows.get(0).isFavorite());
        assertEquals("h2", rows.get(1).getHash());
        assertTrue(rows.get(1).isFavorite());
        assertEquals("Stew", rows.get(1).getName());
        assertEquals(2.5, rows.get(1).getHunger(), 0.0001);
        assertEquals(40, rows.get(1).getEnergy());
        assertEquals(1, rows.get(1).getIngredients().size());
        assertEquals(1, rows.get(1).getFeps().size());
        assertEquals(100.0, rows.get(1).getSmokingWoods().get("Oak"), 0.0);
        assertFalse(rows.get(1).getIngredients().containsKey("Oak"));
    }

    @Test
    void retainsSmokingWoodsFromLoadedRecipes() {
        Recipe smoked = recipe("h-sm", "Smoked");
        smoked.getSmokingWoods().put("Willow", Double.valueOf(40.0));
        FakeSource source = FakeSource.ready(Collections.singletonList(smoked), Collections.<String>emptySet());
        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);
        assertTrue(loader.start());

        Recipe row = loader.recipes().get(0);
        assertEquals(100.0, row.getSmokingWoods().get("Oak"), 0.0);
        assertEquals(40.0, row.getSmokingWoods().get("Willow"), 0.0);
        assertFalse(row.getIngredients().containsKey("Oak"));
        assertFalse(row.getIngredients().containsKey("Willow"));
    }

    @Test
    void emptyCatalogIsReadyWithNoRows() {
        FakeSource source = FakeSource.ready(Collections.<Recipe>emptyList(), Collections.<String>emptySet());
        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);
        assertTrue(loader.start());

        assertTrue(loader.ready.get());
        assertTrue(loader.recipes().isEmpty());
        assertNull(loader.error());
    }

    @Test
    void nullCatalogIsTreatedAsEmpty() {
        FakeSource source = FakeSource.ready(null, null);
        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);
        assertTrue(loader.start());

        assertTrue(loader.ready.get());
        assertTrue(loader.recipes().isEmpty());
        assertNull(loader.error());
    }

    @Test
    void skipsNullRecipesAndNullHashes() {
        Recipe ok = recipe("ok", "Ok");
        FakeSource source = FakeSource.ready(Arrays.asList(null, ok, recipe(null, "NoHash")), setOf("ok"));
        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);
        assertTrue(loader.start());

        List<Recipe> rows = loader.recipes();
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).isFavorite());
        assertFalse(rows.get(1).isFavorite());
    }

    @Test
    void errorSetsMessageAndReadyWithEmptyRows() {
        FakeSource source = FakeSource.ready(Collections.singletonList(recipe("h", "X")), setOf("h"));
        source.error = new SQLException("catalog down");
        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);

        assertTrue(loader.start());
        assertTrue(loader.ready.get());
        assertTrue(loader.recipes().isEmpty());
        assertEquals("catalog down", loader.error());
        assertEquals(1, source.loadCalls.get());
    }

    @Test
    void skipWhenSourceNotReadyDoesNotLoad() {
        FakeSource source = FakeSource.ready(Collections.singletonList(recipe("h", "X")), setOf("h"));
        source.ready = false;
        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);

        assertFalse(loader.start());
        assertFalse(loader.ready.get());
        assertTrue(loader.recipes().isEmpty());
        assertNull(loader.error());
        assertEquals(0, source.loadCalls.get());
    }

    @Test
    void enqueueSkipsWhenNotReady() {
        FakeSource source = FakeSource.ready(Collections.singletonList(recipe("h", "X")), Collections.<String>emptySet());
        source.ready = false;
        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);
        AtomicInteger submitted = new AtomicInteger();

        Future<?> future = loader.enqueue(task -> {
            submitted.incrementAndGet();
            return null;
        });

        assertNull(future);
        assertEquals(0, submitted.get());
        assertFalse(loader.ready.get());
    }

    @Test
    void enqueueRunsOffCallerAndSetsReady() throws Exception {
        FakeSource source = FakeSource.ready(Collections.singletonList(recipe("async", "Roast")), setOf("async"));
        RecipeCatalogLoader loader = new RecipeCatalogLoader(source);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        try {
            Future<?> future = loader.enqueue(task -> pool.submit(() -> {
                started.countDown();
                task.run();
            }));
            assertNotNull(future);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            future.get(2, TimeUnit.SECONDS);
            assertTrue(loader.ready.get());
            assertEquals(1, loader.recipes().size());
            assertTrue(loader.recipes().get(0).isFavorite());
            assertNull(loader.error());
        } finally {
            pool.shutdownNow();
        }
    }

    private static Recipe recipe(String hash, String name) {
        HashMap<String, Recipe.IngredientInfo> ingredients = new HashMap<String, Recipe.IngredientInfo>();
        HashMap<String, Recipe.Fep> feps = new HashMap<String, Recipe.Fep>();
        if (hash != null) {
            ingredients.put("Carrot", new Recipe.IngredientInfo(50.0, "gfx/invobjs/carrot"));
            feps.put("Strength +1", new Recipe.Fep(1.2, 1.0));
        }
        HashMap<String, Double> woods = new HashMap<String, Double>();
        if (hash != null) {
            woods.put("Oak", Double.valueOf(100.0));
        }
        return new Recipe(hash, name, "gfx/invobjs/" + name, 2.5, 40, ingredients, woods, feps);
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    private static final class FakeSource implements RecipeCatalogLoader.CatalogSource {
        boolean ready = true;
        List<Recipe> recipes = new ArrayList<Recipe>();
        Set<String> favorites = new HashSet<String>();
        Exception error;
        final AtomicInteger loadCalls = new AtomicInteger();

        static FakeSource ready(List<Recipe> recipes, Set<String> favorites) {
            FakeSource source = new FakeSource();
            source.recipes = recipes;
            source.favorites = favorites;
            return source;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public List<Recipe> loadRecipes() throws Exception {
            loadCalls.incrementAndGet();
            if (error != null) {
                throw error;
            }
            return recipes;
        }

        @Override
        public Set<String> loadFavoriteHashes() throws Exception {
            if (error != null) {
                throw error;
            }
            return favorites;
        }
    }
}
