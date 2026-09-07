package nurgling.hotkeys.presets;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyBinding;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyPresetCatalogTest {
    @Test void defaultPresetContainsEveryRegisteredDefault() {
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("key", InputGesture.none()));
        registry.register(action("mouse", InputGesture.mouse(3, 7, 0)));

        HotkeyPreset preset = HotkeyPresetCatalog.defaultPreset(registry);

        assertEquals(HotkeyPresetCatalog.DEFAULT_ID, preset.id());
        assertEquals("Default", preset.name());
        assertTrue(preset.builtIn());
        assertEquals(InputGesture.none(), preset.gesture("key"));
        assertEquals(InputGesture.mouse(3, 7, 0), preset.gesture("mouse"));
        assertEquals(2, preset.gestures().size());
    }

    @Test void presetDefensivelyCopiesItsGestureSnapshot() {
        Map<String, InputGesture> source = new HashMap<>();
        source.put("item.take", InputGesture.none());
        HotkeyPreset preset = new HotkeyPreset("user-1", " Mine ", false, source);

        source.clear();

        assertEquals("Mine", preset.name());
        assertEquals(InputGesture.none(), preset.gesture("item.take"));
        assertThrows(UnsupportedOperationException.class,
                () -> preset.gestures().put("x", InputGesture.none()));
    }

    @Test void builtInCatalogCannotBeMutated() {
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("only", InputGesture.none()));

        List<HotkeyPreset> builtIns = HotkeyPresetCatalog.builtIns(registry);

        assertEquals(1, builtIns.size());
        assertThrows(UnsupportedOperationException.class, builtIns::clear);
    }

    private static HotkeyAction action(String id, InputGesture gesture) {
        HotkeyBinding binding = new HotkeyBinding() {
            private InputGesture current = gesture;
            public String id() { return id; }
            public InputGesture defaultGesture() { return gesture; }
            public InputGesture current() { return current; }
            public void set(InputGesture value) { current = value; }
            public void reset() { current = gesture; }
        };
        EnumSet<InputGesture.Type> allowed = gesture.type() == InputGesture.Type.NONE
                ? EnumSet.allOf(InputGesture.Type.class) : EnumSet.of(gesture.type());
        return new HotkeyAction(id, null, id, HotkeyCategory.ALL,
                EnumSet.of(HotkeyContext.GLOBAL), allowed, binding, null, 0, false);
    }
}
