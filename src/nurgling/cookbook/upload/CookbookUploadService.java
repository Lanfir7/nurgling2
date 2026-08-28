package nurgling.cookbook.upload;

import nurgling.cookbook.Recipe;
import org.json.JSONArray;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CookbookUploadService implements AutoCloseable {
    private static final int MAX_BATCH = 500;
    private static final long FLUSH_INTERVAL_MS = 10_000;

    private final BooleanSupplier enabled;
    private final Supplier<String> endpoint;
    private final Supplier<String> genus;
    private final CookbookHttpClient http;
    private final Executor executor;
    private final Consumer<String> errorReporter;
    private final ExecutorService ownedExecutor;
    private final CookbookUploadBuffer buffer = new CookbookUploadBuffer(MAX_BATCH);
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private final AtomicLong sharingGeneration = new AtomicLong();
    private final Object sharingLock = new Object();
    private volatile long lastFlushAttempt;

    public CookbookUploadService(BooleanSupplier enabled, Supplier<String> endpoint, Supplier<String> genus) {
        this(enabled, endpoint, genus, new CookbookHttpClient(5_000, 10_000), newUploadExecutor());
    }

    CookbookUploadService(BooleanSupplier enabled, Supplier<String> endpoint, Supplier<String> genus,
                          CookbookHttpClient http, Executor executor) {
        this(enabled, endpoint, genus, http, executor, System.err::println);
    }

    CookbookUploadService(BooleanSupplier enabled, Supplier<String> endpoint, Supplier<String> genus,
                          CookbookHttpClient http, Executor executor, Consumer<String> errorReporter) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.genus = genus;
        this.http = http;
        this.executor = executor;
        this.errorReporter = errorReporter;
        this.ownedExecutor = executor instanceof ExecutorService ? (ExecutorService) executor : null;
    }

    public boolean submit(Recipe recipe) {
        return submit(recipe, () -> { });
    }

    public boolean submit(Recipe recipe, Runnable accepted) {
        long generation = sharingGeneration.get();
        if (!enabled.getAsBoolean())
            return false;
        CookbookUploadRecord record = CookbookUploadRecord.from(recipe, genus.get());
        synchronized (sharingLock) {
            if (!enabled.getAsBoolean() || sharingGeneration.get() != generation)
                return false;
            buffer.offer(record);
            accepted.run();
            return true;
        }
    }

    public void tick(long now) {
        if (!enabled.getAsBoolean()) {
            discardBufferedRecipes();
            return;
        }
        if (now - lastFlushAttempt < FLUSH_INTERVAL_MS || !flushInProgress.compareAndSet(false, true))
            return;
        lastFlushAttempt = now;
        try {
            executor.execute(() -> {
                try {
                    flush();
                } finally {
                    flushInProgress.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            flushInProgress.set(false);
        }
    }

    public void settingsChanged() {
        if (!enabled.getAsBoolean()) {
            discardBufferedRecipes();
        } else {
            lastFlushAttempt = 0;
        }
    }

    private void flush() {
        long generation = sharingGeneration.get();
        if (!enabled.getAsBoolean()) {
            discardBufferedRecipes();
            return;
        }
        String target = endpoint.get();
        if (target == null || target.trim().isEmpty())
            return;

        List<CookbookUploadRecord> batch = buffer.drain(MAX_BATCH);
        if (batch.isEmpty())
            return;
        JSONArray payload = new JSONArray();
        for (CookbookUploadRecord record : batch)
            payload.put(record.toJson());
        if (!enabled.getAsBoolean() || sharingGeneration.get() != generation) {
            buffer.clear();
            return;
        }
        try {
            http.post(target, payload);
        } catch (IOException | RuntimeException e) {
            if (enabled.getAsBoolean() && sharingGeneration.get() == generation)
                buffer.restore(batch);
            else
                buffer.clear();
            errorReporter.accept("Cookbook upload failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        discardBufferedRecipes();
        if (ownedExecutor != null)
            ownedExecutor.shutdownNow();
    }

    private void discardBufferedRecipes() {
        synchronized (sharingLock) {
            sharingGeneration.incrementAndGet();
            buffer.clear();
        }
    }

    private static ExecutorService newUploadExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cookbook-uploader");
            thread.setDaemon(true);
            return thread;
        });
    }
}
