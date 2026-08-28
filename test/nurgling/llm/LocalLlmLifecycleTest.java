package nurgling.llm;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLlmLifecycleTest {
    @Test
    void createsSingleProcessGlobalManagerAndDelegatesRepeatedStart() {
        CountingManagerFactory factory = new CountingManagerFactory();
        LocalLlmLifecycle lifecycle = new LocalLlmLifecycle(factory);

        lifecycle.startDesktop();
        lifecycle.startDesktop();

        assertEquals(1, factory.creations.get());
        assertEquals(2, factory.lastManager.startCalls.get());
        assertSame(factory.lastManager, lifecycle.currentManagerForTests());
    }

    @Test
    void stopShutsDownOwnedManagerWithoutDroppingReference() {
        CountingManagerFactory factory = new CountingManagerFactory();
        LocalLlmLifecycle lifecycle = new LocalLlmLifecycle(factory);
        lifecycle.startDesktop();

        lifecycle.stopDesktop();
        lifecycle.stopDesktop();

        assertEquals(1, factory.creations.get());
        assertEquals(1, factory.lastManager.startCalls.get());
        assertEquals(2, factory.lastManager.stopCalls.get());
        assertSame(factory.lastManager, lifecycle.currentManagerForTests());
    }

    @Test
    void concurrentStopAndStartReuseSameManagerInstance() throws Exception {
        BlockingManagerFactory factory = new BlockingManagerFactory();
        LocalLlmLifecycle lifecycle = new LocalLlmLifecycle(factory);
        lifecycle.startDesktop();

        Thread stopper = new Thread(new Runnable() {
            @Override
            public void run() {
                lifecycle.stopDesktop();
            }
        }, "llm-lifecycle-stop-test");
        stopper.start();
        assertTrue(factory.blockingManager.stopEntered.await(1, TimeUnit.SECONDS));
        lifecycle.startDesktop();
        factory.blockingManager.allowStop.countDown();
        stopper.join(2000L);

        assertEquals(1, factory.creations.get());
        assertEquals(2, factory.blockingManager.startCalls.get());
        assertEquals(1, factory.blockingManager.stopCalls.get());
        assertSame(factory.blockingManager, lifecycle.currentManagerForTests());
    }

    @Test
    void statusDelegatesReturnDefaultsWithoutCreatingManager() {
        CountingManagerFactory factory = new CountingManagerFactory();
        LocalLlmLifecycle lifecycle = new LocalLlmLifecycle(factory);

        assertFalse(lifecycle.isAvailable());
        assertFalse(lifecycle.isReady());
        assertEquals(Optional.empty(), lifecycle.getEndpoint());
        assertEquals(LocalLlmState.STOPPED, lifecycle.getState());
        assertEquals(0, factory.creations.get());
    }

    @Test
    void statusDelegatesUseCurrentManagerState() throws Exception {
        CountingManagerFactory factory = new CountingManagerFactory();
        LocalLlmLifecycle lifecycle = new LocalLlmLifecycle(factory);
        lifecycle.startDesktop();
        URI endpoint = new URI("http://127.0.0.1:8080");
        factory.lastManager.setStatus(true, true, endpoint, LocalLlmState.READY);

        assertTrue(lifecycle.isAvailable());
        assertTrue(lifecycle.isReady());
        assertEquals(Optional.of(endpoint), lifecycle.getEndpoint());
        assertEquals(LocalLlmState.READY, lifecycle.getState());
        assertEquals(1, factory.creations.get());
    }

    @Test
    void stopWithoutStartIsNoop() {
        CountingManagerFactory factory = new CountingManagerFactory();
        LocalLlmLifecycle lifecycle = new LocalLlmLifecycle(factory);

        lifecycle.stopDesktop();

        assertEquals(0, factory.creations.get());
    }

    private static class CountingManagerFactory implements LocalLlmLifecycle.ManagerFactory {
        protected final AtomicInteger creations = new AtomicInteger();
        protected CountingManager lastManager;

        @Override
        public LocalLlmManager create() {
            creations.incrementAndGet();
            lastManager = new CountingManager();
            return lastManager;
        }
    }

    private static final class BlockingManagerFactory extends CountingManagerFactory {
        private BlockingManager blockingManager;

        @Override
        public LocalLlmManager create() {
            creations.incrementAndGet();
            blockingManager = new BlockingManager();
            lastManager = blockingManager;
            return blockingManager;
        }
    }

    private static class CountingManager extends LocalLlmManager {
        protected final AtomicInteger startCalls = new AtomicInteger();
        protected final AtomicInteger stopCalls = new AtomicInteger();
        private volatile boolean available;
        private volatile boolean ready;
        private volatile URI endpoint;
        private volatile LocalLlmState state = LocalLlmState.STOPPED;

        private CountingManager() {
            super(() -> null, command -> null, endpoint -> 503, (host, port) -> true, 10L, 10L);
        }

        @Override
        public void start() {
            startCalls.incrementAndGet();
        }

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public Optional<URI> getEndpoint() {
            return endpoint == null ? Optional.<URI>empty() : Optional.of(endpoint);
        }

        @Override
        public LocalLlmState getState() {
            return state;
        }

        void setStatus(boolean available, boolean ready, URI endpoint, LocalLlmState state) {
            this.available = available;
            this.ready = ready;
            this.endpoint = endpoint;
            this.state = state;
        }
    }

    private static final class BlockingManager extends CountingManager {
        private final CountDownLatch stopEntered = new CountDownLatch(1);
        private final CountDownLatch allowStop = new CountDownLatch(1);

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
            stopEntered.countDown();
            try {
                allowStop.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
