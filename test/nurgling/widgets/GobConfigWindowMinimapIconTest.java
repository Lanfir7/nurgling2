package nurgling.widgets;

import haven.GobIcon;
import haven.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GobConfigWindowMinimapIconTest {
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
}
