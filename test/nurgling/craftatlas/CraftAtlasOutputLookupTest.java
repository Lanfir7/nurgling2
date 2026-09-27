package nurgling.craftatlas;

import nurgling.tools.RecipeIngredientCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasOutputLookupTest {
    private static final String ITEM = "Atlas async glue test";

    @AfterEach
    void clearLocalMappings() {
        RecipeIngredientCache.clear();
    }

    @Test
    void delayedLoadDoesNotBlockSelectionAndVisibleTickPicksUpLinksOnce() {
        FakeSource source = new FakeSource();
        ManualExecutor worker = new ManualExecutor();
        source.outputs.put(ITEM, Set.of(new RecipeIngredientCache.RecipeEntry("remote", "Remote")));
        CraftAtlasOutputLookup lookup = new CraftAtlasOutputLookup(source, worker, () -> -100L);
        CraftAtlasEntry remote = CraftAtlasEntry.builder("remote", "Different name").build();
        CraftAtlasController controller = new CraftAtlasController(
                CraftAtlasSnapshot.of(1, Collections.singletonList(remote)), null, lookup::find);

        assertEquals(CraftRecipeGraph.LinkState.NONE, controller.linkState("unobserved", ITEM));
        assertEquals(0, source.loads);
        lookup.requestIfNeeded();
        lookup.requestIfNeeded();
        assertEquals(1, worker.size());
        assertEquals(0, source.loads);
        assertFalse(lookup.takeVisibleChange(false));

        worker.runNext();
        lookup.applyCompleted();
        assertEquals(1, source.loads);
        assertEquals(CraftRecipeGraph.LinkState.SINGLE, controller.linkState("unobserved", ITEM));
        assertFalse(lookup.takeVisibleChange(false));
        assertTrue(lookup.takeVisibleChange(true));
        assertFalse(lookup.takeVisibleChange(true));
        lookup.requestIfNeeded();
        assertEquals(0, worker.size());
        lookup.close();
    }

    @Test
    void localMappingsRemainVisibleAndMergeWithBackgroundResults() {
        FakeSource source = new FakeSource();
        ManualExecutor worker = new ManualExecutor();
        CraftAtlasOutputLookup lookup = new CraftAtlasOutputLookup(source, worker, System::nanoTime);
        CraftAtlasEntry local = CraftAtlasEntry.builder("local", "Other local name").build();
        CraftAtlasEntry remote = CraftAtlasEntry.builder("remote", "Other remote name").build();
        RecipeIngredientCache.addOutputMapping(ITEM, "local", "Local");
        CraftAtlasController controller = new CraftAtlasController(
                CraftAtlasSnapshot.of(1, Set.of(local, remote)), null, lookup::find);
        assertEquals(CraftRecipeGraph.LinkState.SINGLE, controller.linkState("unobserved", ITEM));

        source.outputs.put(ITEM, Set.of(new RecipeIngredientCache.RecipeEntry("remote", "Remote")));
        lookup.requestIfNeeded();
        worker.runNext();
        lookup.applyCompleted();
        assertEquals(CraftRecipeGraph.LinkState.MULTIPLE, controller.linkState("unobserved", ITEM));
        lookup.close();
    }

    @Test
    void failureRetriesAfterCooldownWithoutQueueingEveryTick() {
        FakeSource source = new FakeSource();
        ManualExecutor worker = new ManualExecutor();
        AtomicLong clock = new AtomicLong(-100);
        CraftAtlasOutputLookup lookup = new CraftAtlasOutputLookup(source, worker, clock::get);
        source.ready = false;
        lookup.requestIfNeeded();
        assertEquals(0, worker.size());
        source.ready = true;
        source.fail = true;
        lookup.requestIfNeeded();
        worker.runNext();
        lookup.applyCompleted();
        assertEquals(1, source.loads);

        lookup.requestIfNeeded();
        clock.addAndGet(TimeUnit.SECONDS.toNanos(5) - 1);
        lookup.requestIfNeeded();
        assertEquals(0, worker.size());
        clock.incrementAndGet();
        source.fail = false;
        lookup.requestIfNeeded();
        assertEquals(1, worker.size());
        worker.runNext();
        lookup.applyCompleted();
        assertEquals(2, source.loads);
        lookup.close();
    }

    @Test
    void managerChangeRejectsLateResultAndCloseCancelsQueuedLoad() {
        FakeSource source = new FakeSource();
        ManualExecutor worker = new ManualExecutor();
        CraftAtlasOutputLookup lookup = new CraftAtlasOutputLookup(source, worker, System::nanoTime);
        Object nextManager = new Object();
        source.outputs.put(ITEM, Set.of(new RecipeIngredientCache.RecipeEntry("stale", "Stale")));
        source.afterLoad = () -> source.manager = nextManager;
        lookup.requestIfNeeded();
        worker.runNext(); // The old manager finishes after the active manager has changed.
        lookup.applyCompleted();
        assertTrue(lookup.find(ITEM).isEmpty());

        source.afterLoad = null;
        source.outputs.put(ITEM, Set.of(new RecipeIngredientCache.RecipeEntry("current", "Current")));
        lookup.requestIfNeeded();
        worker.runNext();
        lookup.applyCompleted();
        assertEquals("current", lookup.find(ITEM).iterator().next().paginaResource);

        source.manager = new Object();
        lookup.requestIfNeeded();
        lookup.close();
        worker.runNext();
        assertEquals(2, source.loads);
        assertTrue(lookup.find(ITEM).isEmpty());
    }

    private static final class FakeSource implements CraftAtlasOutputLookup.Source {
        Object manager = new Object();
        boolean ready = true;
        boolean fail;
        int loads;
        Runnable afterLoad;
        final Map<String, Set<RecipeIngredientCache.RecipeEntry>> outputs = new HashMap<>();

        public Object current() { return manager; }
        public boolean ready(Object manager) { return ready; }
        public Map<String, Set<RecipeIngredientCache.RecipeEntry>> load(Object manager) {
            loads++;
            if(afterLoad != null) afterLoad.run();
            if(fail) throw new IllegalStateException("database unavailable");
            return outputs;
        }
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); }
        void runNext() { tasks.remove().run(); }
    }
}
