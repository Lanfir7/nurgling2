package nurgling.hotkeys;

import haven.KeyMatch;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyDraftModelTest {
    @Test void replacementDisablesOldActionAndSaveIsAtomicFromTheModel() {
        HotkeyRegistryTest.MemoryBinding old = binding("old", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, 0)));
        HotkeyRegistryTest.MemoryBinding next = binding("next", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_W, 0)));
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("old", old));
        registry.register(action("next", next));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        HotkeyConflict conflict = draft.assign("next", old.current()).get(0);
        draft.replace(conflict);
        assertEquals(InputGesture.Type.NONE, draft.effective("old").type());
        assertEquals(KeyEvent.VK_W, next.current().key().code);
        draft.save();
        assertEquals(InputGesture.Type.NONE, old.current().type());
        assertEquals(KeyEvent.VK_Q, next.current().key().code);
    }

    @Test void cancelAndCategoryResetDoNotWriteBindings() {
        HotkeyRegistryTest.MemoryBinding binding = binding("map", InputGesture.mouse(1, KeyMatch.MODS, 0));
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("map", binding));
        InputGesture original = binding.current();
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        draft.resetCategory(HotkeyCategory.MAP);
        draft.cancel();
        assertEquals(original, binding.current());
    }

    @Test void conflictCancelRestoresPriorResetOperation() {
        HotkeyRegistryTest.MemoryBinding old = binding("old", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, 0)));
        HotkeyRegistryTest.MemoryBinding next = binding("next", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_W, 0)));
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("old", old));
        registry.register(action("next", next));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        draft.reset("next");
        HotkeyDraftModel.Checkpoint beforeConflict = draft.checkpoint();
        assertFalse(draft.assign("next", old.current()).isEmpty());
        draft.restore(beforeConflict);
        assertEquals(next.defaultGesture(), draft.effective("next"));
        assertTrue(draft.isDirty());
    }

    private static HotkeyRegistryTest.MemoryBinding binding(String id, InputGesture gesture) {
        return new HotkeyRegistryTest.MemoryBinding(id, gesture);
    }

    private static HotkeyAction action(String id, HotkeyBinding binding) {
        return new HotkeyAction(id, null, null, HotkeyCategory.MAP,
                EnumSet.of(HotkeyContext.GLOBAL), EnumSet.of(binding.defaultGesture().type()),
                binding, null, 0, false);
    }
}
