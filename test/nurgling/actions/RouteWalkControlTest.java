package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RouteWalkControlTest {
    @Test
    void pendingLaunchCountsAsRunningUntilFinished() {
        RouteWalkControl control = new RouteWalkControl();
        assertTrue(control.running());
        control.finish("routewalker.finished");
        assertFalse(control.running());
        assertEquals("routewalker.finished", control.statusKey());
    }

    @Test
    void resumeWakesPausedRunner() throws Exception {
        RouteWalkControl control = new RouteWalkControl();
        control.setPaused(true);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean resumed = new AtomicBoolean(false);
        Thread runner = new Thread(() -> {
            entered.countDown();
            try {
                control.awaitResume();
                resumed.set(true);
            } catch (InterruptedException ignored) { }
        });
        control.attachThread(runner);
        runner.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        control.setPaused(false);
        runner.join(1_000);
        assertFalse(runner.isAlive());
        assertTrue(resumed.get());
    }

    @Test
    void cancelWakesAndInterruptsPausedRunner() throws Exception {
        RouteWalkControl control = new RouteWalkControl();
        control.setPaused(true);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread runner = new Thread(() -> {
            entered.countDown();
            try {
                control.awaitResume();
            } catch (InterruptedException expected) {
                interrupted.set(true);
            }
        });
        control.attachThread(runner);
        runner.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        control.cancel();
        runner.join(1_000);
        assertFalse(runner.isAlive());
        assertTrue(control.isCancelled());
        assertTrue(interrupted.get());
    }

    @Test
    void combinedAbortPreservesBothSources() {
        AtomicBoolean caller = new AtomicBoolean(false);
        AtomicBoolean zone = new AtomicBoolean(false);
        assertFalse(PathFinder.combineAbort(caller::get, zone::get).getAsBoolean());
        zone.set(true);
        assertTrue(PathFinder.combineAbort(caller::get, zone::get).getAsBoolean());
        zone.set(false);
        caller.set(true);
        assertTrue(PathFinder.combineAbort(caller::get, zone::get).getAsBoolean());
    }
}
