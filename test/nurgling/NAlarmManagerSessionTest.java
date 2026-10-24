package nurgling;

import haven.ActAudio;
import haven.Audio;
import haven.UI;
import haven.Utils;
import haven.iosys.audio.AudioSystem;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NAlarmManagerSessionTest {
    @BeforeAll static void config() { NConfig.getGlobalInstance(); }

    private static final Audio.CS TONE = (buf, len) -> {
        for (double[] channel : buf)
            java.util.Arrays.fill(channel, 0, len, 1.0);
        return len;
    };

    private static final class SilentSink implements AudioSystem.SinkLine {
        Audio.CS stream;
        public AudioSystem.Player open(Audio.CS stream, int bufsize) {
            this.stream = stream;
            return async -> {};
        }
        public AudioSystem.Player open(Audio.CS stream) { return open(stream, 0); }
    }

    private static NUI bareUI() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return (NUI) unsafeClass.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), NUI.class);
    }

    private static double sample(Audio.CS stream) {
        double[][] out = new double[2][1];
        assertEquals(1, stream.get(out, 1));
        return out[0][0];
    }

    @Test void alarmBypassesSessionMuteAtInterfaceAndMasterVolume() throws Exception {
        String originalAui = Utils.getpref("sfxvol-aui", "1.0");
        String originalMaster = Utils.getpref("sfxvol", "1.0");
        try {
            SilentSink sink = new SilentSink();
            Audio.Root sys = new Audio.Root(sink);
            sys.volume(0.5);
            ActAudio.Root session = new ActAudio.Root(sys);
            NUI ui = bareUI();
            Field audio = UI.class.getDeclaredField("audio");
            audio.setAccessible(true);
            audio.set(ui, session);
            session.aui.setvolume(0.4);
            session.setmuted(true);
            NAlarmManager.sfx(ui, TONE);
            assertEquals(0.2, sample(sink.stream), 1e-9);
            sys.mixer.clear();
            session.aui.setvolume(0);
            NAlarmManager.sfx(ui, TONE);
            assertEquals(0, sample(sink.stream), 1e-9);
        } finally {
            Utils.setpref("sfxvol-aui", originalAui);
            Utils.setpref("sfxvol", originalMaster);
        }
    }

    @Test void throttleIsPerUiAndRetriesAfterFailureOrTickRollback() throws Exception {
        NUI first = bareUI(), second = bareUI();
        first.tickId = 50;
        second.tickId = 50;
        AtomicInteger firstSounds = new AtomicInteger(), secondSounds = new AtomicInteger();
        java.util.function.BiConsumer<UI, Audio.CS> output = (ui, clip) -> {
            (ui == first ? firstSounds : secondSounds).incrementAndGet();
        };
        NAlarmManager.play(first, "alarm/test", name -> TONE, output);
        NAlarmManager.play(first, "alarm/test", name -> TONE, output);
        NAlarmManager.play(second, "alarm/test", name -> TONE, output);
        assertEquals(1, firstSounds.get());
        assertEquals(1, secondSounds.get());
        first.tickId = 49;
        NAlarmManager.play(first, "alarm/test", name -> TONE, output);
        assertEquals(2, firstSounds.get());
        first.tickId = 2000;
        assertThrows(IllegalStateException.class, () ->
                NAlarmManager.play(first, "alarm/test", name -> { throw new IllegalStateException("load"); }, output));
        NAlarmManager.play(first, "alarm/test", name -> TONE, output);
        assertEquals(3, firstSounds.get());
    }

    @Test void blockedLoadDoesNotBlockOtherSessionsAndExplicitOwnerOverridesThreadUi() throws Exception {
        NUI first = bareUI(), second = bareUI();
        first.tickId = second.tickId = 90;
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger firstSounds = new AtomicInteger(), secondSounds = new AtomicInteger();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        NUI previous = ThreadLocalUI.get();
        try {
            Future<?> pending = pool.submit(() -> NAlarmManager.play(first, "alarm/concurrent", name -> {
                entered.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("load timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return TONE;
            }, (ui, clip) -> firstSounds.incrementAndGet()));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            ThreadLocalUI.set(first);
            NAlarmManager.play(second, "alarm/concurrent", name -> TONE,
                    (ui, clip) -> { assertSame(second, ui); secondSounds.incrementAndGet(); });
            assertEquals(1, secondSounds.get());
            release.countDown();
            pending.get(1, TimeUnit.SECONDS);
            assertEquals(1, firstSounds.get());
            ThreadLocalUI.set(second);
            NAlarmManager.play("alarm/compat", name -> TONE,
                    (ui, clip) -> { assertSame(second, ui); secondSounds.incrementAndGet(); });
            assertEquals(2, secondSounds.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
            if (previous == null) ThreadLocalUI.clear();
            else ThreadLocalUI.set(previous);
        }
    }
}
