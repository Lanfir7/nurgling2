package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GobIconSettingsOverrideTest {
    @Test
    void temporaryShowOverrideIsEffectiveButNotSerialized() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting setting = setting("gfx/terobjs/mm/trees/oak", new Object[] {"ripe"}, false);
        settings.settings.put(setting.id, setting);
        byte[] before = encoded(settings);

        settings.setShowOverride(setting.id, true);

        assertTrue(settings.shown(setting));
        assertFalse(setting.show);
        assertArrayEquals(before, encoded(settings));

        settings.setShowOverride(setting.id, null);
        assertFalse(settings.shown(setting));
    }

    @Test
    void overridesAreKeyedByFullSettingId() {
        GobIcon.Settings settings = new GobIcon.Settings(null, "test-icons");
        GobIcon.Setting plain = setting("gfx/terobjs/mm/trees/oak", new Object[0], false);
        GobIcon.Setting ripe = setting("gfx/terobjs/mm/trees/oak", new Object[] {"ripe"}, false);

        settings.setShowOverride(ripe.id, true);

        assertFalse(settings.shown(plain));
        assertTrue(settings.shown(ripe));
    }

    private static GobIcon.Setting setting(String name, Object[] sub, boolean show) {
        Resource.Saved saved = new Resource.Saved(Resource.remote(), name, 1);
        GobIcon.Settings.ResID from = new GobIcon.Settings.ResID(saved, new byte[0]);
        GobIcon.Setting setting = new GobIcon.Setting(saved, sub, null, from);
        setting.show = show;
        return setting;
    }

    private static byte[] encoded(GobIcon.Settings settings) {
        MessageBuf out = new MessageBuf();
        settings.save(out);
        return out.fin();
    }
}
