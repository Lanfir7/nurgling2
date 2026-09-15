package nurgling.widgets;

import haven.GobIcon;
import haven.Resource;
import nurgling.NConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GobConfigWindowMinimapIconTest {
    @BeforeAll
    static void initClientBits() {
        if (NConfig.current == null)
            NConfig.current = new NConfig();
        Resource.local().add(new Resource.FileSource(Paths.get("resources", "compiled", "res")));
    }
    private static class TrackingSettings extends GobIcon.Settings {
        int saves;

        TrackingSettings() {
            super(null, "test-icons");
        }

        @Override
        public void dsave() {
            saves++;
        }
    }

    private static GobIcon.Setting setting() {
        return new GobIcon.Setting(
                new Resource.Saved(Resource.remote(), "gfx/invobjs/quartz", 0),
                GobIcon.Icon.nilid);
    }

    @Test
    void enablingMinimapIconChangesDisplayPreferenceAndSaves() {
        TrackingSettings settings = new TrackingSettings();
        GobIcon.Setting icon = setting();

        GobConfigWindow.applyMinimapIconSetting(settings, icon, true);

        assertTrue(icon.show);
        assertFalse(icon.markset);
        assertEquals(1, settings.saves);
    }

    @Test
    void disablingMinimapIconChangesDisplayPreferenceAndSaves() {
        TrackingSettings settings = new TrackingSettings();
        GobIcon.Setting icon = setting();
        icon.show = true;

        GobConfigWindow.applyMinimapIconSetting(settings, icon, false);

        assertFalse(icon.show);
        assertFalse(icon.markset);
        assertEquals(1, settings.saves);
    }

    @Test
    void enablingNotifyTurnsOnSoundAndMinimapIconAndSaves() {
        TrackingSettings settings = new TrackingSettings();
        GobIcon.Setting icon = setting();

        GobConfigWindow.applyNotifySetting(settings, icon, true);

        assertTrue(icon.notify);
        assertTrue(icon.show);
        assertEquals(1, settings.saves);
    }

    @Test
    void disablingNotifyKeepsMinimapIconAndSaves() {
        TrackingSettings settings = new TrackingSettings();
        GobIcon.Setting icon = setting();
        icon.show = true;
        icon.notify = true;

        GobConfigWindow.applyNotifySetting(settings, icon, false);

        assertFalse(icon.notify);
        assertTrue(icon.show);
        assertEquals(1, settings.saves);
    }

    @Test
    void applyingBuiltinSoundStoresResourceAndSaves() {
        TrackingSettings settings = new TrackingSettings();
        GobIcon.Setting icon = setting();
        icon.filens = Paths.get("old.wav");

        GobConfigWindow.applySoundSetting(settings, icon,
                new GobIcon.NotificationSetting("Bell 1", "sfx/hud/mmap/bell1"));

        assertEquals("sfx/hud/mmap/bell1", icon.resns);
        assertNull(icon.filens);
        assertEquals(1, settings.saves);
    }

    @Test
    void applyingWavSoundStoresPathAndClearsResourceAndSaves() {
        TrackingSettings settings = new TrackingSettings();
        GobIcon.Setting icon = setting();
        icon.resns = "sfx/hud/mmap/bell1";

        GobConfigWindow.applySoundSetting(settings, icon,
                new GobIcon.NotificationSetting(Paths.get("AlarmSounds", "boar.wav")));

        assertNull(icon.resns);
        assertEquals(Paths.get("AlarmSounds", "boar.wav"), icon.filens);
        assertEquals(1, settings.saves);
    }
}
