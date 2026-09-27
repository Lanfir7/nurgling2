package nurgling.craftatlas;

import nurgling.NCore;
import nurgling.db.DatabaseManager;
import nurgling.db.dao.CraftRecipeDao;
import nurgling.tools.RecipeIngredientCache;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Background DB output lookup used only to enrich Atlas links. UI reads completed snapshots. */
public final class CraftAtlasOutputLookup implements AutoCloseable {
    private static final long RETRY_DELAY_NS = TimeUnit.SECONDS.toNanos(5);
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "CraftAtlas-OutputLookup");
                thread.setDaemon(true);
                return thread;
            });

    public interface Source {
        Object current();
        boolean ready(Object manager);
        Map<String, Set<RecipeIngredientCache.RecipeEntry>> load(Object manager) throws Exception;
    }

    private final Source source;
    private final Executor executor;
    private final LongSupplier clock;
    private final ConcurrentLinkedQueue<Result> completed = new ConcurrentLinkedQueue<>();
    private Object manager;
    private Map<String, Set<RecipeIngredientCache.RecipeEntry>> outputs = Collections.emptyMap();
    private FutureTask<Void> job;
    private long generation;
    private long revision;
    private long shownRevision;
    private long retryAt = Long.MIN_VALUE;
    private boolean loaded;
    private boolean closed;

    public CraftAtlasOutputLookup() {
        this(new Source() {
            public Object current() { return NCore.databaseManager; }
            public boolean ready(Object manager) { return ((DatabaseManager) manager).isReady(); }
            public Map<String, Set<RecipeIngredientCache.RecipeEntry>> load(Object manager) throws Exception {
                DatabaseManager db = (DatabaseManager) manager;
                Map<String, Map<String, Set<RecipeIngredientCache.RecipeEntry>>> all =
                        db.executeOperation(adapter -> new CraftRecipeDao().loadAll(adapter));
                return all.getOrDefault(RecipeIngredientCache.TYPE_OUTPUT, Collections.emptyMap());
            }
        }, WORKER, System::nanoTime);
    }

    CraftAtlasOutputLookup(Source source, Executor executor, LongSupplier clock) {
        this.source = source;
        this.executor = executor;
        this.clock = clock;
    }

    /** Never performs DB I/O or starts work; safe during row building and while hidden. */
    public Set<RecipeIngredientCache.RecipeEntry> find(String itemName) {
        refreshManager();
        if(closed || itemName == null) return Collections.emptySet();
        return outputs.getOrDefault(itemName, Collections.emptySet());
    }

    /** Called on the UI tick; a completed load changes only this lookup's immutable snapshot. */
    public void applyCompleted() {
        refreshManager();
        Result result;
        while((result = completed.poll()) != null) {
            if(closed || result.generation != generation || result.manager != manager)
                continue;
            job = null;
            if(result.error == null) {
                outputs = result.outputs;
                loaded = true;
                revision++;
            } else {
                retryAt = clock.getAsLong() + RETRY_DELAY_NS;
            }
        }
    }

    /** Called only while the Atlas is visible. One queued/running load per current manager. */
    public void requestIfNeeded() {
        refreshManager();
        if(closed || manager == null || loaded || job != null ||
                (retryAt != Long.MIN_VALUE && clock.getAsLong() - retryAt < 0) ||
                !source.ready(manager)) return;
        final Object requestedManager = manager;
        final long requestedGeneration = generation;
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                completed.add(new Result(requestedManager, requestedGeneration,
                        immutable(source.load(requestedManager)), null));
            } catch(Throwable error) {
                completed.add(new Result(requestedManager, requestedGeneration,
                        Collections.emptyMap(), error));
            }
            return null;
        });
        job = task;
        try {
            executor.execute(task);
        } catch(RejectedExecutionException full) {
            job = null;
            retryAt = clock.getAsLong() + RETRY_DELAY_NS;
        }
    }

    public long revision() {
        refreshManager();
        return revision;
    }

    /** Keep hidden revisions pending until the next visible UI tick. */
    public boolean takeVisibleChange(boolean visible) {
        if(!visible) return false;
        long current = revision();
        if(current == shownRevision) return false;
        shownRevision = current;
        return true;
    }

    private void refreshManager() {
        if(closed) return;
        Object current = source.current();
        if(current == manager) return;
        cancelJob();
        manager = current;
        outputs = Collections.emptyMap();
        loaded = false;
        retryAt = Long.MIN_VALUE;
        generation++;
        revision++;
    }

    private void cancelJob() {
        if(job == null) return;
        job.cancel(true);
        if(executor instanceof ThreadPoolExecutor)
            ((ThreadPoolExecutor) executor).remove(job);
        job = null;
    }

    @Override
    public void close() {
        if(closed) return;
        closed = true;
        generation++;
        cancelJob();
        completed.clear();
        outputs = Collections.emptyMap();
    }

    private static Map<String, Set<RecipeIngredientCache.RecipeEntry>> immutable(
            Map<String, Set<RecipeIngredientCache.RecipeEntry>> source) {
        if(source == null || source.isEmpty()) return Collections.emptyMap();
        Map<String, Set<RecipeIngredientCache.RecipeEntry>> copy = new HashMap<>();
        for(Map.Entry<String, Set<RecipeIngredientCache.RecipeEntry>> entry : source.entrySet())
            copy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
        return Collections.unmodifiableMap(copy);
    }

    private static final class Result {
        final Object manager;
        final long generation;
        final Map<String, Set<RecipeIngredientCache.RecipeEntry>> outputs;
        final Throwable error;

        Result(Object manager, long generation,
               Map<String, Set<RecipeIngredientCache.RecipeEntry>> outputs, Throwable error) {
            this.manager = manager;
            this.generation = generation;
            this.outputs = outputs;
            this.error = error;
        }
    }
}
