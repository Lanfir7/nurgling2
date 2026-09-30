package nurgling.widgets;

import haven.*;
import nurgling.ClientResourceFixture;
import nurgling.NConfig;
import nurgling.i18n.L10n;
import nurgling.render.NGfx;
import nurgling.widgets.options.GraphicsSettings;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsSettingsMethodsTest {
    @Test void actualMethodButtonsUpdateSettingsAndPresetRefreshesTheirSelection() throws Exception {
        Field snapshot = NGfx.class.getDeclaredField("cur"), version = NGfx.class.getDeclaredField("version");
        snapshot.setAccessible(true); version.setAccessible(true);
        Object oldSnapshot = snapshot.get(null), oldVersion = version.get(null);
        NConfig oldConfig = NConfig.current;
        try(ClientResourceFixture resources = new ClientResourceFixture()) {
            NConfig.current = new NConfig();
            NGfx.set(NGfx.Preset.CLASSIC.settings(NGfx.get()));
            GraphicsSettings panel = new GraphicsSettings();
            assertTrue(button(panel, "SMAA 1x").mousedown(new Widget.MouseDownEvent(Coord.z, 1)));
            assertTrue(button(panel, "CAS").mousedown(new Widget.MouseDownEvent(Coord.z, 1)));
            assertTrue(button(panel, "HBAO").mousedown(new Widget.MouseDownEvent(Coord.z, 1)));
            assertEquals(1, NGfx.get().aamethod); assertEquals(1, NGfx.get().sharpmethod); assertEquals(1, NGfx.get().aomethod);
            assertFalse(NGfx.get().fxaa); assertFalse(NGfx.get().sharpen); assertFalse(NGfx.get().ssao);

            CheckBox enabled = panel.children(CheckBox.class).stream()
                    .filter(box -> L10n.get("gfx.antialias").equals(box.label())).findFirst().get();
            enabled.set(true);
            assertTrue(NGfx.get().fxaa);
            assertEquals(1, NGfx.get().aamethod);

            NGfx.set(NGfx.Preset.ENHANCED.settings(NGfx.get()));
            panel.load();
            assertTrue(button(panel, "FXAA").a); assertFalse(button(panel, "SMAA 1x").a);
            assertFalse(button(panel, "CAS").a); assertTrue(button(panel, "SSAO").a);
            NGfx.set(NGfx.Preset.ULTRA.settings(NGfx.get()));
            panel.load();
            assertTrue(button(panel, "SMAA 1x").a); assertTrue(button(panel, "CAS").a); assertTrue(button(panel, "HBAO").a);
            assertFalse(button(panel, "FXAA").a); assertFalse(button(panel, "SSAO").a);
            assertEquals(1, NGfx.get().aamethod);
            assertEquals(1, NGfx.get().sharpmethod);
            assertEquals(1, NGfx.get().aomethod);
        } finally {
            NConfig.current = oldConfig;
            snapshot.set(null, oldSnapshot); version.set(null, oldVersion);
        }
    }

    private static RadioGroup.RadioButton button(GraphicsSettings panel, String label) {
        return panel.children(RadioGroup.RadioButton.class).stream().filter(button -> label.equals(button.label())).findFirst().get();
    }
}
