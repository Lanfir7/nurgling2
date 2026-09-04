package nurgling.sessions;

import haven.Audio;
import haven.Coord;
import haven.UI;
import haven.iosys.audio.AudioSystem;
import nurgling.NConfig;
import nurgling.NUI;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionContextCloseTest {
    @Test
    void closeDrainsCommandsWithoutHoldingUiMonitor() throws Exception {
        NConfig.getGlobalInstance();
        NUI ui = new NUI(null, silentAudio(), Coord.of(1, 1), null);
        SessionContext context = new SessionContext(null, ui);
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch attemptUiLock = new CountDownLatch(1);

        ui.queue.submit(new UI.Command(() -> {
            actionStarted.countDown();
            await(attemptUiLock);
            synchronized (ui) {
                // Queued UI work legitimately needs the UI monitor.
            }
        }));
        assertTrue(actionStarted.await(2, java.util.concurrent.TimeUnit.SECONDS));

        Thread close = new Thread(context::close, "session-close-test");
        close.setDaemon(true);
        close.start();
        assertTrue(waitingWithin(close, Duration.ofSeconds(2)),
                "close did not reach CommandQueue.drain");

        attemptUiLock.countDown();
        close.join(2000);
        assertFalse(close.isAlive(),
                "close retained the UI monitor while waiting for queued UI work");
    }

    private static boolean waitingWithin(Thread thread, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING)
                return true;
            Thread.sleep(5);
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    private static Audio.Root silentAudio() {
        return new Audio.Root(new AudioSystem.SinkLine() {
            private final AudioSystem.Player player = async -> {};

            @Override
            public AudioSystem.Player open(Audio.CS stream, int bufsize) {
                return player;
            }

            @Override
            public AudioSystem.Player open(Audio.CS stream) {
                return player;
            }
        });
    }
}
