package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ExploredAreaSaveTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetSaveExecutor() {
        ExploredArea.resetExecutor();
    }

    @Test
    void mergeSaveDoesNotDeadlockOnItsOwnLockFile() {
        ExploredArea explored = new ExploredArea(null);
        Path target = tempDir.resolve("explored.json");

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> explored.mergeAndSaveToFile(target.toString()));
    }

    @Test
    void mergeSavePreservesExistingExploration() throws Exception {
        Path target = tempDir.resolve("explored-existing.json");
        ExploredArea existing = new ExploredArea(null);
        existing.updateExploredTiles(Coord.z, Coord.of(1, 1), 7);
        Files.write(target, existing.toJson().toString()
                .getBytes(StandardCharsets.UTF_8));

        new ExploredArea(null).mergeAndSaveToFile(target.toString());

        assertTrue(new String(Files.readAllBytes(target), StandardCharsets.UTF_8)
                .contains("\"seg\":7"));
    }

    @Test
    void snapshotCopyDoesNotSeeLaterTileUpdates() {
        ExploredArea explored = new ExploredArea(null);
        explored.updateExploredTiles(Coord.z, Coord.of(1, 1), 3);

        Map<?, boolean[]> snap = explored.snapshotGridMasks();
        assertEquals(1, snap.size());
        boolean[] frozen = snap.values().iterator().next();
        boolean[] frozenCopy = Arrays.copyOf(frozen, frozen.length);

        explored.updateExploredTiles(Coord.z, Coord.of(2, 2), 3);

        assertTrue(Arrays.equals(frozenCopy, frozen));
    }

    @Test
    void requestMergeAndSaveCompletesOffCallerAndWritesFile() throws Exception {
        ExploredArea explored = new ExploredArea(null);
        explored.updateExploredTiles(Coord.z, Coord.of(1, 1), 9);
        Path target = tempDir.resolve("async.json");
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        boolean submitted = explored.requestMergeAndSave(target.toString(), done::countDown,
                () -> {
                    failures.incrementAndGet();
                    done.countDown();
                });

        assertTrue(submitted);
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(0, failures.get());
        assertTrue(new String(Files.readAllBytes(target), StandardCharsets.UTF_8)
                .contains("\"seg\":9"));
    }

    @Test
    void failedAsyncSaveInvokesFailureCallback() throws Exception {
        ExploredArea explored = new ExploredArea(null);
        explored.updateExploredTiles(Coord.z, Coord.of(1, 1), 4);
        Path notAFile = tempDir.resolve("not-a-file");
        Files.createDirectory(notAFile);
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        assertTrue(explored.requestMergeAndSave(notAFile.toString(),
                () -> {
                    successes.incrementAndGet();
                    done.countDown();
                },
                done::countDown));

        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals(0, successes.get());
    }

    @Test
    void saveExecutorKeepsOnlyLatestPendingRun() throws Exception {
        Method factory = ExploredArea.class.getDeclaredMethod("createExecutor");
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
