package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckGridsStateTest {
    @Test
    void movementChecksKeepOnlyLatestPendingRun() throws Exception {
        Method factory = CheckGridsState.class.getDeclaredMethod("createExecutor");
        factory.setAccessible(true);
        ExecutorService executor = (ExecutorService) factory.invoke(null);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<Integer> completed = new CopyOnWriteArrayList<>();

        try {
            executor.execute(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                completed.add(1);
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            executor.execute(() -> completed.add(2));
            executor.execute(() -> completed.add(3));
            releaseFirst.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        assertEquals(Arrays.asList(1, 3), completed);
    }
}
