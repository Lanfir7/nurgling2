package nurgling.hotkeys;

import haven.KeyMatch;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalHotkeyValidationTest {
    @Test void shiftedAndLayoutDependentCharactersOverlapTheirCapturedPhysicalEvents() {
        for(Object[] sample : new Object[][]{{'!', KeyEvent.VK_1, KeyEvent.SHIFT_DOWN_MASK, KeyMatch.S},
                {'@', KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK, KeyMatch.C | KeyMatch.M},
                {'ф', KeyEvent.VK_A, 0, 0}, {'1', KeyEvent.VK_NUMPAD1, 0, 0}}) {
            KeyEvent event = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 0,
                    (int)sample[2], (int)sample[1], (char)sample[0]);
            InputGesture character = InputGesture.key(KeyMatch.forchar((char)sample[0], (int)sample[3]));
            InputGesture captured = InputGesture.key(KeyMatch.forevent(event, KeyMatch.MODS));
            assertTrue(character.matches(event, 0));
            assertTrue(captured.matches(event, 0));
            assertConflict(character, captured);
        }
    }

    @Test void quickChatReassignedToSessionShortcutIsAGlobalConflict() {
        HotkeyRegistry registry = defaultCatalog();
        InputGesture session = registry.find("session-next").defaultGesture();
        assertTrue(registry.conflicts("chat-quick", session).stream()
                .anyMatch(c -> c.conflictingAction().id().equals("session-next")));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        draft.assign("chat-quick", session);
        assertTrue(draft.conflicts().stream().anyMatch(c -> c.action().id().equals("chat-quick") || c.conflictingAction().id().equals("chat-quick")));
        assertThrows(IllegalStateException.class, draft::save);
    }

    private static HotkeyRegistry defaultCatalog() {
        HotkeyRegistry source = new HotkeyRegistry();
        HotkeyCatalog.registerCore(source);
        HotkeyRegistry registry = new HotkeyRegistry();
        for(HotkeyAction a : source.snapshot())
            registry.register(new HotkeyAction(a.id(), a.labelKey(), a.literalLabel(), a.category(),
                    a.contexts(), a.allowedTypes(), new HotkeyRegistryTest.MemoryBinding(a.id(), a.defaultGesture()),
                    a.canonicalMods(), a.order(), a.dynamic()));
        return registry;
    }

    @Test void fullCatalogDefaultsAndResetCanSave() {
        HotkeyRegistry registry = defaultCatalog();
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        List<String> conflicts = new ArrayList<>();
        for(HotkeyConflict c : draft.conflicts()) conflicts.add(c.action().id() + " / " + c.conflictingAction().id());
        assertTrue(conflicts.isEmpty(), conflicts.toString());
        assertDoesNotThrow(draft::save);
        draft.assign("equ", InputGesture.none());
        assertDoesNotThrow(draft::save);
        draft.resetAll();
        assertDoesNotThrow(draft::save);
        assertFalse(draft.isDirty());
    }

    @Test void capturedControlEConflictsWithCharacterBinding() {
        KeyEvent event = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 0,
                KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_E, (char)5);
        InputGesture legacy = InputGesture.key(KeyMatch.forchar('E', KeyMatch.C));
        InputGesture captured = InputGesture.key(KeyMatch.forevent(event, KeyMatch.MODS));
        assertTrue(legacy.matches(event, 0));
        assertTrue(captured.matches(event, 0));
        assertConflict(legacy, captured);
    }

    @Test void partiallyIgnoredModifiersConflictWhenAnEventMatchesBoth() {
        assertConflict(InputGesture.mouse(1, KeyMatch.C, KeyMatch.C),
                InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C | KeyMatch.S));
    }

    @Test void legacyIgnoredModifiersParticipateInRuntimeConflicts() {
        haven.KeyBinding legacy = haven.KeyBinding.get("test/final/ignored-shift",
                KeyMatch.forcode(KeyEvent.VK_E, KeyMatch.C), KeyMatch.S);
        legacy.set(null);
        InputGesture effective = new KeyBindingHotkey(legacy).current();
        assertConflict(effective, InputGesture.key(KeyMatch.forcode(KeyEvent.VK_E, KeyMatch.C | KeyMatch.S)));
    }

    private static void assertConflict(InputGesture left, InputGesture right) {
        HotkeyRegistry registry = new HotkeyRegistry();
        for(String id : Arrays.asList("left", "right")) {
            InputGesture g = id.equals("left") ? left : right;
            registry.register(new HotkeyAction(id, null, id, HotkeyCategory.WORLD,
                    EnumSet.of(HotkeyContext.WORLD_SURFACE), EnumSet.of(g.type()),
                    new HotkeyRegistryTest.MemoryBinding(id, g), null, 0, false));
        }
        assertEquals(1, registry.conflicts("left", left).size());
        assertEquals(1, new HotkeyDraftModel(registry).conflicts().size());
    }

    @Test void corruptAndCrossFamilyPreferencesFallBackAndHeal() {
        Map<String, String> data = new HashMap<>();
        PreferenceStore store = new PreferenceStore() {
            public String get(String key, String fallback) { return data.getOrDefault(key, fallback); }
            public void set(String key, String value) { data.put(key, value); }
        };
        InputGesture def = InputGesture.mouse(1, KeyMatch.MODS, 0);
        for(String encoded : Arrays.asList("w:1:7:0", "m:2", "b:0:7:0", "b:-1:7:0", "b:1:8:0", "b:1:7:8", "b:1:1:2")) {
            data.put("gesturebind/test", encoded);
            assertEquals(def, new GestureBinding("test", def, store).current(), encoded);
            assertEquals("", data.get("gesturebind/test"), encoded);
        }
        for(String encoded : Arrays.asList("w:0:7:0", "w:2:7:0", "w:-9:7:0", "m:0", "m:3", "m:8"))
            assertThrows(IllegalArgumentException.class, () -> InputGesture.decode(encoded), encoded);
    }

    @Test void capturedWheelDirectionIsDisplayedCorrectly() {
        assertEquals("Wheel Up", nurgling.widgets.nsettings.HotkeyCapturePolicy.wheel(-1, 0,
                Hotkeys.action(Hotkeys.INVENTORY_TRANSFER_TO_MAIN)).gesture().displayName());
        assertEquals("Wheel Down", nurgling.widgets.nsettings.HotkeyCapturePolicy.wheel(1, 0,
                Hotkeys.action(Hotkeys.INVENTORY_TRANSFER_TO_MAIN)).gesture().displayName());
    }
}
