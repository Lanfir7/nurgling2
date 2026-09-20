package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class BotsInterruptWidgetRegistryTest {
    @Test void retainsNewThreadAndCoalescesDuplicateRegistration() {
        BotsInterruptWidget registry = new BotsInterruptWidget();
        Thread thread = new Thread(() -> { }, "registry-test");

        registry.addObserve(thread);
        registry.addObserve(thread);

        assertTrue(registry.waitBot.get());
        assertEquals(1, registry.getRunningBots().size());
        assertSame(thread, registry.getRunningBots().get(0).getThread());
        registry.tick(0);
        assertEquals(1, registry.getRunningBots().size(), "NEW is not a finished bot");
    }

    @Test void formatsTheInnermostActionFrameAsACompactStatus() {
        StackTraceElement outer = new StackTraceElement("nurgling.actions.Action", "run", "Action.java", 12);
        StackTraceElement inner = new StackTraceElement("nurgling.actions.bots.Forager$1", "collect", "Forager.java", 42);

        assertEquals("Forager", BotsInterruptWidget.formatActionStatus(new StackTraceElement[]{inner, outer}));
    }

    @Test void removesTerminatedThreadDuringLifecycleTick() throws InterruptedException {
        BotsInterruptWidget registry = new BotsInterruptWidget();
        Thread thread = new Thread(() -> { }, "finished-registry-test");
        registry.addObserve(thread);
        thread.start();
        thread.join();

        registry.tick(0);

        assertFalse(registry.hasRunningBots());
        assertFalse(registry.waitBot.get());
    }

    @Test void stopOneInterruptsOnlyItsRegisteredThread() throws InterruptedException {
        BotsInterruptWidget registry = new BotsInterruptWidget();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicInteger interrupted = new AtomicInteger();
        Thread first = new Thread(() -> {
            ready.countDown();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
            }
        }, "first");
        Thread second = new Thread(() -> { }, "second");
        Thread foreign = new Thread(() -> { }, "foreign");
        registry.addObserve(first);
        registry.addObserve(second);
        first.start();
        assertTrue(ready.await(1, TimeUnit.SECONDS));

        registry.removeObserve(foreign);
        assertEquals(2, registry.getRunningBots().size());

        registry.removeObserve(first);
        first.join(1000);
        assertEquals(1, interrupted.get());
        assertEquals(1, registry.getRunningBots().size());
        assertSame(second, registry.getRunningBots().get(0).getThread());
    }

    @Test void stopAllInterruptsEveryTrackedBot() throws InterruptedException {
        BotsInterruptWidget registry = new BotsInterruptWidget();
        CountDownLatch ready = new CountDownLatch(2);
        AtomicInteger interrupted = new AtomicInteger();
        Runnable work = () -> {
            ready.countDown();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
            }
        };
        Thread first = new Thread(work, "one");
        Thread second = new Thread(work, "two");
        registry.addObserve(first);
        registry.addObserve(second);
        first.start();
        second.start();
        assertTrue(ready.await(1, TimeUnit.SECONDS));

        registry.interruptAll();
        first.join(1000);
        second.join(1000);

        assertFalse(registry.hasRunningBots());
        assertFalse(registry.waitBot.get());
        assertEquals(2, interrupted.get());
    }
}
