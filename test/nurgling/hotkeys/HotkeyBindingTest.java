package nurgling.hotkeys;

import haven.KeyBinding;
import haven.KeyMatch;
import haven.Utils;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyBindingTest {
    @Test void legacyAdapterKeepsTheOriginalPreferenceIdentity() {
        KeyBinding key = KeyBinding.get("test/unified-hotkeys/legacy",
                KeyMatch.forcode(KeyEvent.VK_Q, 0));
        KeyBindingHotkey binding = new KeyBindingHotkey(key);
        binding.set(InputGesture.key(KeyMatch.forcode(KeyEvent.VK_E, KeyMatch.C)));
        assertEquals("test/unified-hotkeys/legacy", binding.id());
        assertEquals(KeyEvent.VK_E, key.key().code);
        binding.reset();
        assertEquals(KeyEvent.VK_Q, key.key().code);
    }

    @Test void gestureBindingLoadsCustomDisabledAndCorruptValues() {
        MemoryPreferences prefs = new MemoryPreferences();
        InputGesture def = InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S);
        GestureBinding binding = new GestureBinding("item-transfer", def, prefs);
        binding.set(InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C));
        assertEquals(binding.current(), new GestureBinding("item-transfer", def, prefs).current());
        binding.set(InputGesture.none());
        assertEquals(InputGesture.Type.NONE, binding.current().type());
        prefs.set("gesturebind/item-transfer", "broken");
        assertEquals(def, new GestureBinding("item-transfer", def, prefs).current());
    }

    @Test void legacyAdapterRejectsNonKeyboardGestures() {
        KeyBinding key = KeyBinding.get("test/unified-hotkeys/reject",
                KeyMatch.forcode(KeyEvent.VK_Q, 0));
        KeyBindingHotkey binding = new KeyBindingHotkey(key);
        assertThrows(IllegalArgumentException.class,
                () -> binding.set(InputGesture.mouse(1, KeyMatch.MODS, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> binding.set(InputGesture.wheel(1, KeyMatch.MODS, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> binding.set(InputGesture.modifier(KeyMatch.C)));
    }

    @Test void gestureBindingRejectsKeyboardGesturesAndResetRestoresDefault() {
        MemoryPreferences prefs = new MemoryPreferences();
        InputGesture def = InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S);
        GestureBinding binding = new GestureBinding("item-transfer-reset", def, prefs);
        assertThrows(IllegalArgumentException.class,
                () -> binding.set(InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, 0))));
        binding.set(InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C));
        binding.reset();
        assertEquals(def, binding.current());
        assertEquals("", prefs.get("gesturebind/item-transfer-reset", "missing"));
    }

    @Test void legacyCheckpointPreservesExplicitOverrideEqualToDefault() {
        String id = "test/unified-hotkeys/checkpoint-explicit-default";
        String preferenceKey = "keybind/" + id;
        KeyMatch defaultKey = KeyMatch.forcode(KeyEvent.VK_Q, KeyMatch.C);
        KeyBinding key = KeyBinding.get(id, defaultKey);
        try {
            key.set(defaultKey);
            KeyBindingHotkey binding = new KeyBindingHotkey(key);
            Object checkpoint = binding.checkpoint();
            key.set(KeyMatch.forcode(KeyEvent.VK_E, 0));

            binding.restore(checkpoint);

            assertTrue(key.set(), "explicit default override must remain explicit");
            assertEquals(KeyMatch.reduce(defaultKey), Utils.getpref(preferenceKey, null));
            assertEquals(KeyEvent.VK_Q, key.key.code);
        } finally {
            Utils.setpref(preferenceKey, null);
            key.key = null;
        }
    }

    private static class MemoryPreferences implements PreferenceStore {
        private final Map<String, String> values = new HashMap<>();

        @Override public String get(String key, String fallback) {
            String value = values.get(key);
            return value == null ? fallback : value;
        }

        @Override public void set(String key, String value) {
            values.put(key, value);
        }
    }
}
