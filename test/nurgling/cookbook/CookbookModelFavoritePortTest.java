package nurgling.cookbook;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class CookbookModelFavoritePortTest {
    @Test
    void toggleFavoriteFlipsRowAndPersistsThroughPort() {
        Recipe recipe = new Recipe("h1", "Pie", "gfx/invobjs/food", 1.0, 50,
                Collections.emptyMap(), Collections.emptyMap());
        CookbookModel model = new CookbookModel();
        model.setRecipes(Collections.singletonList(recipe));
        final List<Boolean> desired = new ArrayList<Boolean>();
        model.setFavoritePort((hash, want) -> {
            assertEquals("h1", hash);
            desired.add(Boolean.valueOf(want));
        });

        CookbookRow row = model.view().get(0);
        assertFalse(row.recipe.isFavorite());
        int version = model.version();
        int query = model.queryVersion();
        model.toggleFavorite(row);
        assertTrue(row.recipe.isFavorite());
        assertEquals(Collections.singletonList(Boolean.TRUE), desired);
        assertEquals(query, model.queryVersion());
        assertTrue(model.version() > version);
        assertTrue(model.view().get(0).recipe.isFavorite());

        model.toggleFavorite(row);
        assertFalse(row.recipe.isFavorite());
        assertEquals(2, desired.size());
        assertEquals(Boolean.TRUE, desired.get(0));
        assertEquals(Boolean.FALSE, desired.get(1));
    }

    @Test
    void toggleFavoriteRevertsWhenPortFails() {
        Recipe recipe = new Recipe("h2", "Stew", null, 1.0, 10,
                Collections.emptyMap(), Collections.emptyMap());
        CookbookModel model = new CookbookModel();
        model.setRecipes(Collections.singletonList(recipe));
        model.setFavoritePort((hash, want) -> {
            throw new IllegalStateException("db down");
        });
        CookbookRow row = model.view().get(0);
        model.toggleFavorite(row);
        assertFalse(row.recipe.isFavorite());
    }

    @Test
    void managerPortIgnoresNullManager() throws Exception {
        CookbookModel.FavoritePort port = CookbookModel.managerPort(null);
        port.persist("unused", true);
    }

    @Test
    void persistTrueAddsOnlyAfterQueuedRunnableRuns() throws Exception {
        RecordingManager manager = new RecordingManager();
        QueueingEnqueue enqueue = new QueueingEnqueue();
        CookbookModel.FavoritePort port = CookbookModel.asyncManagerPort(manager, enqueue);

        port.persist("h1", true);

        assertEquals(1, enqueue.queued.size());
        assertTrue(manager.added.isEmpty());
        assertTrue(manager.removed.isEmpty());

        enqueue.queued.get(0).run();
        assertEquals(Collections.singletonList("h1"), manager.added);
        assertTrue(manager.removed.isEmpty());
    }

    @Test
    void persistFalseRemoves() throws Exception {
        RecordingManager manager = new RecordingManager();
        QueueingEnqueue enqueue = new QueueingEnqueue();
        CookbookModel.FavoritePort port = CookbookModel.asyncManagerPort(manager, enqueue);

        port.persist("h2", false);

        assertEquals(1, enqueue.queued.size());
        assertTrue(manager.removed.isEmpty());

        enqueue.queued.get(0).run();
        assertEquals(Collections.singletonList("h2"), manager.removed);
        assertTrue(manager.added.isEmpty());
    }

    @Test
    void rapidPersistEnqueuesExactDesiredStates() throws Exception {
        RecordingManager manager = new RecordingManager();
        QueueingEnqueue enqueue = new QueueingEnqueue();
        CookbookModel.FavoritePort port = CookbookModel.asyncManagerPort(manager, enqueue);

        port.persist("h3", true);
        port.persist("h3", false);

        assertEquals(2, enqueue.queued.size());
        assertTrue(manager.added.isEmpty());
        assertTrue(manager.removed.isEmpty());

        enqueue.queued.get(0).run();
        assertEquals(Collections.singletonList("h3"), manager.added);
        assertTrue(manager.removed.isEmpty());

        enqueue.queued.get(1).run();
        assertEquals(Collections.singletonList("h3"), manager.added);
        assertEquals(Collections.singletonList("h3"), manager.removed);
    }

    @Test
    void nullSubmitRollsBackOptimisticStar() {
        Recipe recipe = new Recipe("h4", "Cake", null, 1.0, 10,
                Collections.emptyMap(), Collections.emptyMap());
        CookbookModel model = new CookbookModel();
        model.setRecipes(Collections.singletonList(recipe));
        model.setFavoritePort(CookbookModel.asyncManagerPort(new RecordingManager(), new CookbookModel.Enqueue() {
            @Override
            public Future<?> submit(Runnable task) {
                return null;
            }
        }));
        CookbookRow row = model.view().get(0);
        assertFalse(row.recipe.isFavorite());
        model.toggleFavorite(row);
        assertFalse(row.recipe.isFavorite());
    }

    @Test
    void syncPortThrowStillRollsBack() {
        Recipe recipe = new Recipe("h5", "Soup", null, 1.0, 10,
                Collections.emptyMap(), Collections.emptyMap());
        CookbookModel model = new CookbookModel();
        model.setRecipes(Collections.singletonList(recipe));
        model.setFavoritePort((hash, want) -> {
            throw new IllegalStateException("db down");
        });
        CookbookRow row = model.view().get(0);
        model.toggleFavorite(row);
        assertFalse(row.recipe.isFavorite());
    }

    private static final class RecordingManager extends FavoriteRecipeManager {
        final List<String> added = new ArrayList<String>();
        final List<String> removed = new ArrayList<String>();

        RecordingManager() {
            super(null);
        }

        @Override
        public void addFavorite(String recipeHash) {
            added.add(recipeHash);
        }

        @Override
        public void removeFavorite(String recipeHash) {
            removed.add(recipeHash);
        }

        @Override
        public void toggleFavorite(String recipeHash) {
            fail("toggleFavorite must not be called");
        }
    }

    private static final class QueueingEnqueue implements CookbookModel.Enqueue {
        final List<Runnable> queued = new ArrayList<Runnable>();

        @Override
        public Future<?> submit(Runnable task) {
            queued.add(task);
            return CompletableFuture.completedFuture(null);
        }
    }
}
