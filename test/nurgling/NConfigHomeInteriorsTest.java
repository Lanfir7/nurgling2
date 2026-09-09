package nurgling;

import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.HomeInteriorStore;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NConfigHomeInteriorsTest {
    @Test
    void defaultRegistryIsEmptyAndPartitionedByGenus() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            assertTrue(HomeInteriorStore.load("world-one").bindings().isEmpty());
            assertTrue(HomeInteriorStore.load("world-two").bindings().isEmpty());
        } finally {
            NConfig.current = previous;
        }
    }

    @Test
    void updateKeepsLearnedAndManualBindingsIsolatedByWorld() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            HomeInteriorStore.update("world-one", registry -> registry.put(learnedBinding()));
            assertEquals(1, HomeInteriorStore.load("world-one").bindings().size());
            assertTrue(HomeInteriorStore.load("world-two").bindings().isEmpty());

            HomeInteriorStore.update("world-two", registry ->
                    registry.markManual(7L, Collections.singleton(11L), "Cellar"));
            assertEquals(1, HomeInteriorStore.load("world-one").bindings().size());
            assertEquals(1, HomeInteriorStore.load("world-two").bindings().size());

            Object stored = NConfig.getGlobal(NConfig.Key.homeInteriors);
            assertEquals(1, HomeInteriorRegistry.decodeForWorld(stored, "world-one").bindings().size());
            assertEquals(1, HomeInteriorRegistry.decodeForWorld(stored, "world-two").bindings().size());
        } finally {
            NConfig.current = previous;
        }
    }

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
                    HomeInteriorStore.update("world-one", registry -> {
                        firstInsideUpdate.countDown();
                        try {
                            releaseFirst.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(error);
                        }
                        return registry.put(learnedBinding());
                    });
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            Thread second = new Thread(() -> {
                try {
                    secondStarted.countDown();
                    HomeInteriorStore.update("world-two", registry ->
                            registry.markManual(7L, Collections.singleton(11L), "Cellar"));
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
            Object stored = NConfig.getGlobal(NConfig.Key.homeInteriors);
            assertEquals(1, HomeInteriorRegistry.decodeForWorld(stored, "world-one").bindings().size());
            assertEquals(1, HomeInteriorRegistry.decodeForWorld(stored, "world-two").bindings().size());
        } finally {
            releaseFirst.countDown();
            NConfig.current = previous;
        }
    }

    @Test
    void stagedRemovalPreservesBindingLearnedWhileSettingsWereOpen() {
        HomeInteriorRegistry baseline = registryWithBindings("old-home");
        HomeInteriorRegistry concurrent = baseline.put(binding("new-home"));

        HomeInteriorRegistry result = concurrent.applyRemovals(
                Collections.singleton("old-home"));

        assertNull(result.find("old-home"));
        assertNotNull(result.find("new-home"));
        assertTrue(result.isSuppressed(portalOf("old-home")));
    }

    private static HomeInteriorRegistry registryWithBindings(String id) {
        return HomeInteriorRegistry.empty().put(binding(id));
    }

    private static HomeInteriorRegistry.Binding binding(String id) {
        return HomeInteriorRegistry.Binding.automatic(
                id, 1L, Collections.singleton(1L),
                Collections.singleton(HomeInteriorRegistry.OriginKey.parse("claim-owner:" + id)),
                portalOf(id), id, 1L);
    }

    private static HomeInteriorRegistry.PortalIdentity portalOf(String id) {
        return new HomeInteriorRegistry.PortalIdentity(1L, 0, 0, "gfx/" + id);
    }

    private static HomeInteriorRegistry.Binding learnedBinding() {
        return HomeInteriorRegistry.Binding.automatic(
                "auto:42:7:9:gfx/terobjs/arch/stonemansion", 9001L,
                Collections.singleton(1001L),
                Collections.singleton(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9,
                        "gfx/terobjs/arch/stonemansion"),
                "Lanfir's Claim -> Stone Mansion", 1234L);
    }
}
