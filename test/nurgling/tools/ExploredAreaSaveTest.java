package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void tileUpdateCanProceedWhileSavePublishesOtherGrids() throws Exception {
        ExploredArea explored = new ExploredArea(null);
        explored.updateExploredTiles(Coord.z, Coord.of(1, 1), 3);
        CountDownLatch atSecondGrid = new CountDownLatch(1);
        CountDownLatch continuePublication = new CountDownLatch(1);
        Map<Object, boolean[]> masks = new LinkedHashMap<>();
        boolean[] first = new boolean[10_000];
        first[1] = true; // Disk exploration on the same grid.
        masks.put(gridKey(3, 0, 0), first);
        boolean[] second = new boolean[10_000];
        second[0] = true;
        masks.put(gridKey(3, 1, 0), second);

        FutureTask<Void> publish = new FutureTask<>(() -> {
            Method method = ExploredArea.class.getDeclaredMethod("publishMerged", Map.class, long.class);
            method.setAccessible(true);
            method.invoke(explored, pausingOnSecondEntry(masks, atSecondGrid, continuePublication), 0L);
            return null;
        });
        Thread publisher = new Thread(publish, "test-publish");
        publisher.start();
        try {
            assertTrue(atSecondGrid.await(2, TimeUnit.SECONDS));
            FutureTask<Void> update = new FutureTask<>(() -> {
                explored.updateExploredTiles(Coord.of(2, 2), Coord.of(3, 3), 3);
                return null;
            });
            Thread updater = new Thread(update, "test-exploration");
            updater.start();
            update.get(1, TimeUnit.SECONDS);
            assertTrue(explored.getExploredMaskForGrid(Coord.z, 3, 0).mask[2 + 2 * 100]);
        } finally {
            continuePublication.countDown();
            publish.get(2, TimeUnit.SECONDS);
        }
        boolean[] combined = explored.getExploredMaskForGrid(Coord.z, 3, 0).mask;
        assertTrue(combined[0]);
        assertTrue(combined[1]);
        assertTrue(combined[2 + 2 * 100]);
        assertTrue(explored.gridGeneration(1, 0, 3, false) > 0);
    }

    @Test
    void clearCanProceedDuringPublicationAndStaleGridsStayCleared() throws Exception {
        ExploredArea explored = new ExploredArea(null);
        explored.updateExploredTiles(Coord.z, Coord.of(1, 1), 8);
        CountDownLatch atSecondGrid = new CountDownLatch(1);
        CountDownLatch continuePublication = new CountDownLatch(1);
        Map<Object, boolean[]> masks = new LinkedHashMap<>();
        boolean[] first = new boolean[10_000];
        first[1] = true;
        masks.put(gridKey(8, 0, 0), first);
        boolean[] second = new boolean[10_000];
        second[0] = true;
        masks.put(gridKey(8, 1, 0), second);
        FutureTask<Void> publish = new FutureTask<>(() -> {
            Method method = ExploredArea.class.getDeclaredMethod("publishMerged", Map.class, long.class);
            method.setAccessible(true);
            method.invoke(explored, pausingOnSecondEntry(masks, atSecondGrid, continuePublication), 0L);
            return null;
        });
        new Thread(publish, "test-publish-before-clear").start();
        try {
            assertTrue(atSecondGrid.await(2, TimeUnit.SECONDS));
            FutureTask<Void> clear = new FutureTask<>(() -> {
                explored.clear();
                return null;
            });
            new Thread(clear, "test-clear").start();
            clear.get(1, TimeUnit.SECONDS);
        } finally {
            continuePublication.countDown();
            publish.get(2, TimeUnit.SECONDS);
        }
        assertEquals(0, explored.snapshotGridMasks().size());
        assertEquals(0, explored.gridGeneration(1, 0, 8, false));
    }

    private static Object gridKey(long segment, int x, int y) throws Exception {
        Class<?> type = Class.forName("nurgling.tools.ExploredArea$GridKey");
        Constructor<?> ctor = type.getDeclaredConstructor(long.class, Coord.class);
        ctor.setAccessible(true);
        return ctor.newInstance(segment, Coord.of(x, y));
    }

    private static Map<Object, boolean[]> pausingOnSecondEntry(Map<Object, boolean[]> source,
            CountDownLatch arrived, CountDownLatch proceed) {
        return new AbstractMap<Object, boolean[]>() {
            @Override
            public Set<Entry<Object, boolean[]>> entrySet() {
                return new AbstractSet<Entry<Object, boolean[]>>() {
                    @Override public int size() { return source.size(); }
                    @Override public Iterator<Entry<Object, boolean[]>> iterator() {
                        Iterator<Entry<Object, boolean[]>> delegate = source.entrySet().iterator();
                        return new Iterator<Entry<Object, boolean[]>>() {
                            int count;
                            @Override public boolean hasNext() { return delegate.hasNext(); }
                            @Override public Entry<Object, boolean[]> next() {
                                if (++count == 2) {
                                    arrived.countDown();
                                    try {
                                        if (!proceed.await(5, TimeUnit.SECONDS))
                                            throw new AssertionError("publication was not released");
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError(e);
                                    }
                                }
                                return delegate.next();
                            }
                        };
                    }
                };
            }
        };
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
    void twoInstancesBothSaveOnSharedExecutor() throws Exception {
        ExploredArea first = new ExploredArea(null);
        ExploredArea second = new ExploredArea(null);
        first.updateExploredTiles(Coord.z, Coord.of(1, 1), 11);
        second.updateExploredTiles(Coord.z, Coord.of(1, 1), 22);
        Path firstPath = tempDir.resolve("instance-a.json");
        Path secondPath = tempDir.resolve("instance-b.json");
        CountDownLatch done = new CountDownLatch(2);

        assertTrue(first.requestMergeAndSave(firstPath.toString(), done::countDown, done::countDown));
        assertTrue(second.requestMergeAndSave(secondPath.toString(), done::countDown, done::countDown));
        assertTrue(done.await(3, TimeUnit.SECONDS));

        assertTrue(new String(Files.readAllBytes(firstPath), StandardCharsets.UTF_8).contains("\"seg\":11"));
        assertTrue(new String(Files.readAllBytes(secondPath), StandardCharsets.UTF_8).contains("\"seg\":22"));
    }

    @Test
    void overlappingRequestsOnSameInstanceKeepLatestTiles() throws Exception {
        ExploredArea explored = new ExploredArea(null);
        Path target = tempDir.resolve("latest.json");
        CountDownLatch lastDone = new CountDownLatch(1);

        explored.updateExploredTiles(Coord.z, Coord.of(1, 1), 1);
        assertTrue(explored.requestMergeAndSave(target.toString(), null, lastDone::countDown));
        explored.updateExploredTiles(Coord.of(10, 10), Coord.of(11, 11), 2);
        assertTrue(explored.requestMergeAndSave(target.toString(), lastDone::countDown, lastDone::countDown));

        assertTrue(lastDone.await(3, TimeUnit.SECONDS));
        String json = "";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (Files.exists(target)) {
                json = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
                if (json.contains("\"seg\":1") && json.contains("\"seg\":2")) {
                    break;
                }
            }
            Thread.sleep(20);
        }
        assertTrue(json.contains("\"seg\":1"));
        assertTrue(json.contains("\"seg\":2"));
    }
}
