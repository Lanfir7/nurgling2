package nurgling.render;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ModernGraphicsSettingsTest {
    static NGfx.Settings settings(Map<String, Object> map) throws Exception {
        Constructor<NGfx.Settings> ctor = NGfx.Settings.class.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(map);
    }

    @Test void existingConfigurationsKeepTheirRenderingMethods() throws Exception {
        Map<String, Object> old = new HashMap<>();
        old.put("fxaa", true); old.put("sharpen", true); old.put("ssao", true);
        NGfx.Settings s = settings(old);
        assertTrue(s.fxaa); assertTrue(s.sharpen); assertTrue(s.ssao);
        assertEquals(0, s.aamethod); assertEquals(0, s.sharpmethod); assertEquals(0, s.aomethod);
    }

    @Test void methodsSurviveSavingAndOtherSettingChanges() throws Exception {
        NGfx.Settings s = settings(new HashMap<>()).with("aamethod", 1)
                .with("sharpmethod", 1).with("aomethod", 1).with("sharpness", 0.65f);
        NGfx.Settings restored = settings(s.map()).with("water", true);
        assertEquals(1, restored.aamethod); assertEquals(1, restored.sharpmethod);
        assertEquals(1, restored.aomethod); assertEquals(0.65f, restored.sharpness);
        assertTrue(restored.water);
    }

    @Test void invalidMethodsFallBackWithoutChangingOtherSettings() throws Exception {
        Map<String, Object> broken = new HashMap<>();
        broken.put("aamethod", -1); broken.put("sharpmethod", 900); broken.put("aomethod", "HBAO");
        broken.put("exposure", 1.23f);
        NGfx.Settings s = settings(broken);
        assertEquals(0, s.aamethod); assertEquals(0, s.sharpmethod); assertEquals(0, s.aomethod);
        assertEquals(1.23f, s.exposure);
        for(Object value : new Object[] {1.5, Double.NaN, Double.POSITIVE_INFINITY, "1", true}) {
            assertEquals(0, s.with("aamethod", value).aamethod);
            assertEquals(0, s.with("sharpmethod", value).sharpmethod);
            assertEquals(0, s.with("aomethod", value).aomethod);
        }
    }

    @Test void presetsAndClassicFallbackAreExplicit() throws Exception {
        NGfx.Settings base = settings(new HashMap<>());
        NGfx.Settings ultra = NGfx.Preset.ULTRA.settings(base);
        assertTrue(ultra.fxaa); assertTrue(ultra.sharpen); assertTrue(ultra.ssao);
        assertEquals(1, ultra.aamethod); assertEquals(1, ultra.sharpmethod); assertEquals(1, ultra.aomethod);
        NGfx.Settings classic = NGfx.Preset.CLASSIC.settings(ultra);
        assertFalse(classic.fxaa); assertFalse(classic.sharpen); assertFalse(classic.ssao);
        NGfx.Settings enhanced = NGfx.Preset.ENHANCED.settings(base);
        assertEquals(0, enhanced.aamethod); assertEquals(0, enhanced.sharpmethod); assertEquals(0, enhanced.aomethod);
    }
}
