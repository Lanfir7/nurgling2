package nurgling.plugins;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiEventsTest {
    private final List<UiEvents.Listener> listeners = new ArrayList<>();

    @AfterEach
    void removeListeners() {
        for (UiEvents.Listener listener : listeners)
            UiEvents.removeListener(listener);
        listeners.clear();
    }

    private void add(UiEvents.Listener listener) {
        listeners.add(listener);
        UiEvents.addListener(listener);
    }

    @Test
    void listenersAreUniqueAndNullSafeToRemove() {
        AtomicInteger calls = new AtomicInteger();
        UiEvents.Listener counted = new UiEvents.Listener() {
            public void onDestroyWidget(haven.UI ui, int id, haven.Widget wdg) { calls.incrementAndGet(); }
        };
        UiEvents.addListener(null);
        UiEvents.removeListener(null);
        add(counted);
        UiEvents.addListener(counted);

        assertTrue(UiEvents.active());
        UiEvents.fireDestroyWidget(null, 7, null);
        assertEquals(1, calls.get());

        UiEvents.removeListener(counted);
        assertFalse(UiEvents.active());
        UiEvents.fireDestroyWidget(null, 8, null);
        assertEquals(1, calls.get());
    }

    @Test
    void outgoingListenerFailuresAndArgumentMutationAreIsolated() throws Exception {
        Object[] core = {"core", 42};
        AtomicReference<Object[]> first = new AtomicReference<>();
        AtomicReference<Object[]> second = new AtomicReference<>();
        CountDownLatch delivered = new CountDownLatch(1);
        UiEvents.Listener mutating = new UiEvents.Listener() {
            public void onOutgoing(haven.UI ui, int id, haven.Widget sender, String msg, Object[] args) {
                first.set(args);
                args[0] = "changed";
                throw new RuntimeException("expected test failure");
            }
        };
        UiEvents.Listener observing = new UiEvents.Listener() {
            public void onOutgoing(haven.UI ui, int id, haven.Widget sender, String msg, Object[] args) {
                second.set(args);
                delivered.countDown();
            }
        };
        add(mutating);
        add(observing);

        UiEvents.fireOutgoing(null, 3, null, "act", core);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertArrayEquals(new Object[]{"core", 42}, core);
        assertArrayEquals(new Object[]{"changed", 42}, first.get());
        assertArrayEquals(new Object[]{"core", 42}, second.get());
    }

    @Test
    void outgoingNonFatalErrorDoesNotStopLaterListenersOrDispatcher() throws Exception {
        CountDownLatch delivered = new CountDownLatch(2);
        AtomicInteger observed = new AtomicInteger();
        add(new UiEvents.Listener() {
            public void onOutgoing(haven.UI ui, int id, haven.Widget sender, String msg, Object[] args) {
                throw new AssertionError("expected listener error");
            }
        });
        add(new UiEvents.Listener() {
            public void onOutgoing(haven.UI ui, int id, haven.Widget sender, String msg, Object[] args) {
                observed.incrementAndGet();
                delivered.countDown();
            }
        });

        UiEvents.fireOutgoing(null, 3, null, "first", null);
        UiEvents.fireOutgoing(null, 3, null, "second", null);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(2, observed.get());
    }

    @Test
    void outgoingDeliveryIsQueuedAndDoesNotReenterSenderLock() throws Exception {
        Object senderLock = new Object();
        CountDownLatch senderReturned = new CountDownLatch(1);
        CountDownLatch releaseSender = new CountDownLatch(1);
        CountDownLatch observerEntered = new CountDownLatch(1);
        AtomicReference<Object[]> observed = new AtomicReference<>();
        Object[] core = {"snapshot"};
        UiEvents.Listener observer = new UiEvents.Listener() {
            public void onOutgoing(haven.UI ui, int id, haven.Widget sender, String msg, Object[] args) {
                synchronized (senderLock) {
                    observed.set(args);
                    observerEntered.countDown();
                }
            }
        };
        add(observer);

        Thread sender = new Thread(() -> {
            synchronized (senderLock) {
                UiEvents.fireOutgoing(null, 4, null, "queued", core);
                senderReturned.countDown();
                try {
                    releaseSender.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        sender.start();
        try {
            assertTrue(senderReturned.await(2, TimeUnit.SECONDS));
            core[0] = "changed-after-send";
            assertFalse(observerEntered.await(150, TimeUnit.MILLISECONDS));
        } finally {
            releaseSender.countDown();
            sender.join(2000);
        }
        assertFalse(sender.isAlive());
        assertTrue(observerEntered.await(2, TimeUnit.SECONDS));
        assertArrayEquals(new Object[]{"snapshot"}, observed.get());
    }

    @Test
    void acceptedOutgoingEventsKeepTheirOrder() throws Exception {
        CountDownLatch delivered = new CountDownLatch(2);
        List<String> messages = new ArrayList<>();
        add(new UiEvents.Listener() {
            public void onOutgoing(haven.UI ui, int id, haven.Widget sender, String msg, Object[] args) {
                synchronized (messages) {
                    messages.add(msg);
                }
                delivered.countDown();
            }
        });

        UiEvents.fireOutgoing(null, 5, null, "first", null);
        UiEvents.fireOutgoing(null, 5, null, "second", null);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        synchronized (messages) {
            assertEquals(Arrays.asList("first", "second"), messages);
        }
    }

    @Test
    void queuedOutgoingUsesTheListenerSnapshotFromEnqueue() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch staleDelivered = new CountDownLatch(1);
        AtomicInteger newListenerCalls = new AtomicInteger();
        UiEvents.Listener oldListener = new UiEvents.Listener() {
            public void onOutgoing(haven.UI ui, int id, haven.Widget sender, String msg, Object[] args) {
                if (msg.equals("block")) {
                    firstStarted.countDown();
                    try {
                        releaseFirst.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else if (msg.equals("stale")) {
                    staleDelivered.countDown();
                }
            }
        };
        UiEvents.Listener newListener = new UiEvents.Listener() {
            public void onOutgoing(haven.UI ui, int id, haven.Widget sender, String msg, Object[] args) {
                newListenerCalls.incrementAndGet();
            }
        };
        add(oldListener);
        try {
            UiEvents.fireOutgoing(null, 6, null, "block", null);
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            UiEvents.fireOutgoing(null, 6, null, "stale", null);
            UiEvents.removeListener(oldListener);
            add(newListener);
        } finally {
            releaseFirst.countDown();
        }
        assertTrue(staleDelivered.await(2, TimeUnit.SECONDS));
        assertEquals(0, newListenerCalls.get());
    }
}
