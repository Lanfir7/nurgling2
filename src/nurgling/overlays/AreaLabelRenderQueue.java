package nurgling.overlays;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Bounded raster work. Mailboxes do not refer back to their sprites or UI owners. */
final class AreaLabelRenderQueue {
    private static final long RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
    private static final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), task -> {
                Thread thread = new Thread(task, "Area-label-raster");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private AreaLabelRenderQueue() {}

    static final class Mailbox<T> {
        private long generation;
        private boolean pending;
        private boolean closed;
        private T ready;
        private long retryAt;

        synchronized void invalidate() {
            generation++;
            ready = null;
        }

        synchronized boolean canSubmit(long now) {
            return !closed && !pending && ready == null && (retryAt == 0 || now - retryAt >= 0);
        }

        synchronized boolean pending() {
            return pending;
        }

        synchronized long retryAt() {
            return retryAt;
        }

        boolean submit(Supplier<T> render, long now) {
            final long requestGeneration;
            synchronized (this) {
                if (!canSubmit(now))
                    return false;
                pending = true;
                requestGeneration = generation;
            }
            try {
                worker.execute(() -> {
                    T result = null;
                    boolean failed = false;
                    try {
                        synchronized (Mailbox.this) {
                            if (closed || requestGeneration != generation)
                                return;
                        }
                        result = render.get();
                    } catch (RuntimeException | Error error) {
                        failed = true;
                        System.err.println("[AreaLabel] raster failed: " + error.getMessage());
                    } finally {
                        synchronized (Mailbox.this) {
                            pending = false;
                            if (!closed && requestGeneration == generation) {
                                if (failed)
                                    retryAt = System.nanoTime() + RETRY_NANOS;
                                else
                                    ready = result;
                            }
                        }
                    }
                });
                return true;
            } catch (RejectedExecutionException full) {
                synchronized (this) {
                    pending = false;
                    retryAt = now + RETRY_NANOS;
                }
                return false;
            }
        }

        synchronized T take() {
            T result = ready;
            ready = null;
            return result;
        }

        synchronized void close() {
            closed = true;
            generation++;
            ready = null;
        }
    }
}
