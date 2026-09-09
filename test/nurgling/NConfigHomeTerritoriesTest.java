package nurgling;

import nurgling.tools.HomeTerritories;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NConfigHomeTerritoriesTest {
    @Test
    void atomicUpdatePreservesHomesSavedConcurrentlyForDifferentWorlds() throws Exception {
        NConfig previous = NConfig.current;
        NConfig.current = new NConfig();
        CountDownLatch firstInsideUpdate = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            Thread first = new Thread(() -> {
                try {
                    NConfig.update(NConfig.Key.homeTerritories, stored -> {
                        firstInsideUpdate.countDown();
                        try {
                            releaseFirst.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(error);
                        }
                        return HomeTerritories.encodeForWorld(stored, "world-one",
                                Collections.singletonList(new HomeTerritories.Entry(
                                        HomeTerritories.Type.CLAIM, "Lanfir")));
                    });
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            Thread second = new Thread(() -> {
                try {
                    secondStarted.countDown();
                    NConfig.update(NConfig.Key.homeTerritories, stored ->
                            HomeTerritories.encodeForWorld(stored, "world-two",
                                    Collections.singletonList(new HomeTerritories.Entry(
                                            HomeTerritories.Type.VILLAGE, "Oakvale"))));
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });

            first.start();
            assertTrue(firstInsideUpdate.await(2, TimeUnit.SECONDS));
            second.start();
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            releaseFirst.countDown();
            first.join(2000);
            second.join(2000);

            assertNull(failure.get());
            Object stored = NConfig.getGlobal(NConfig.Key.homeTerritories);
            assertEquals(1, HomeTerritories.decodeForWorld(stored, "world-one").size());
            assertEquals(1, HomeTerritories.decodeForWorld(stored, "world-two").size());
        } finally {
            releaseFirst.countDown();
            NConfig.current = previous;
        }
    }
}
