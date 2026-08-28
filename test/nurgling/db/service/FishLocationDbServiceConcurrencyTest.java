package nurgling.db.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishLocationDbServiceConcurrencyTest {
    @Test
    void concurrentStartsNeverScheduleOnAStoppedExecutor() throws Exception {
        BlockingFirstScheduleExecutor firstScheduler = new BlockingFirstScheduleExecutor();
        AtomicInteger schedulerNumber = new AtomicInteger();
        FishLocationDbService service = new FishLocationDbService(null, () ->
                schedulerNumber.getAndIncrement() == 0
                        ? firstScheduler
                        : Executors.newSingleThreadScheduledExecutor());
        ExecutorService callers = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = callers.submit(() -> service.startSync(60));
            assertTrue(firstScheduler.scheduleEntered.await(2, TimeUnit.SECONDS));
            Future<?> second = callers.submit(() -> service.startSync(60));

            assertDoesNotThrow(() -> first.get());
            assertDoesNotThrow(() -> second.get());
        } finally {
            callers.shutdownNow();
            service.stopSync();
        }
    }

    /** Makes the old race deterministic: a competing start can stop this executor before it schedules. */
    private static final class BlockingFirstScheduleExecutor extends ScheduledThreadPoolExecutor {
        private final CountDownLatch scheduleEntered = new CountDownLatch(1);
        private final CountDownLatch shutdownCalled = new CountDownLatch(1);

        private BlockingFirstScheduleExecutor() {
            super(1);
        }

        @Override
        public void shutdown() {
            shutdownCalled.countDown();
            super.shutdown();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                                                       long period, TimeUnit unit) {
            scheduleEntered.countDown();
            try {
                shutdownCalled.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.scheduleAtFixedRate(command, initialDelay, period, unit);
        }
    }
}
