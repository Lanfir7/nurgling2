package haven;

import haven.iosys.audio.AudioSystem;
import nurgling.NUI;
import nurgling.sessions.SessionContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class SessionMuteAudioTest {
    private static class SilentSink implements AudioSystem.SinkLine {
        Audio.CS stream;
        public AudioSystem.Player open(Audio.CS stream, int bufsize) {
            this.stream = stream;
            return async -> {};
        }
        public AudioSystem.Player open(Audio.CS stream) { return open(stream, 0); }
    }

    private static final Audio.CS TONE = (buf, len) -> {
        for (double[] channel : buf)
            java.util.Arrays.fill(channel, 0, len, 1.0);
        return len;
    };

    private static double sample(Audio.Mixer mixer) {
        double[][] out = new double[2][1];
        mixer.get(out, 1);
        return out[0][0];
    }

    private static NUI bareUI(ActAudio.Root audio) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        NUI ui = (NUI) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(singleton.get(null), NUI.class);
        Field audioField = UI.class.getDeclaredField("audio");
        audioField.setAccessible(true);
        audioField.set(ui, audio);
        return ui;
    }

    @Test void sessionContextAdaptersKeepMuteOnOwnerAndFreshSessionStartsAudible() throws Exception {
        Audio.Root shared = new Audio.Root(new SilentSink());
        ActAudio.Root firstAudio = new ActAudio.Root(shared);
        ActAudio.Root secondAudio = new ActAudio.Root(shared);
        SessionContext first = new SessionContext(null, bareUI(firstAudio));
        SessionContext second = new SessionContext(null, bareUI(secondAudio));
        assertFalse(first.isMuted());
        assertFalse(second.isMuted());
        first.setMuted(true);
        assertTrue(first.isMuted());
        assertFalse(second.isMuted());
        second.setMuted(true);
        second.setMuted(false);
        assertTrue(first.isMuted());
        assertFalse(second.isMuted());
        assertFalse(new SessionContext(null, bareUI(new ActAudio.Root(shared))).isMuted());
        SessionContext incomplete = new SessionContext(null, bareUI(null));
        assertFalse(incomplete.isMuted());
        assertDoesNotThrow(() -> incomplete.setMuted(true));
    }

    @Test void muteCoversAllChannelsAndLeavesOtherUiAudible() {
        String[] names = {"aui", "pos", "amb"};
        String[] original = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            original[i] = Utils.getpref("sfxvol-" + names[i], "1.0");
            Utils.setpref("sfxvol-" + names[i], "1.0");
        }
        try {
            Audio.Root shared = new Audio.Root(new SilentSink());
            ActAudio.Root first = new ActAudio.Root(shared);
            ActAudio.Root second = new ActAudio.Root(shared);
            first.aui.add(TONE);
            first.pos.add(TONE);
            first.amb.add(TONE);
            second.aui.add(TONE);
            second.pos.add(TONE);
            second.amb.add(TONE);
            assertEquals(6.0, sample(shared.mixer), 1e-9);
            first.setmuted(true);
            assertTrue(first.muted());
            assertFalse(second.muted());
            assertEquals(3.0, sample(shared.mixer), 1e-9);
            first.setmuted(false);
            assertEquals(6.0, sample(shared.mixer), 1e-9);
        } finally {
            for (int i = 0; i < names.length; i++)
                Utils.setpref("sfxvol-" + names[i], original[i]);
        }
    }

    @Test void volumeChangedWhileMutedRestoresAndRecreatedChannelStaysMuted() {
        String original = Utils.getpref("sfxvol-aui", "1.0");
        try {
            Audio.Root shared = new Audio.Root(new SilentSink());
            ActAudio.Root root = new ActAudio.Root(shared);
            root.aui.add(TONE);
            root.setmuted(true);
            assertEquals(original, Utils.getpref("sfxvol-aui", "1.0"), "mute is not a saved volume change");
            root.aui.setvolume(0.4);
            assertEquals("0.4", Utils.getpref("sfxvol-aui", "1.0"));
            assertEquals(0.0, sample(shared.mixer), 1e-9);
            root.aui.clear();
            root.aui.add(TONE);
            assertEquals(0.0, sample(shared.mixer), 1e-9);
            root.setmuted(false);
            assertEquals(0.4, sample(shared.mixer), 1e-9);
        } finally {
            Utils.setpref("sfxvol-aui", original);
        }
    }
}
