package nurgling.hotkeys;

import haven.KeyMatch;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyDraftModelTest {
    @Test void failedSaveRestoresEarlierBindingsKeepsDraftAndReportsRollbackFailures() {
        InputGesture original = InputGesture.mouse(1, 7, 0);
        HotkeyRegistryTest.MemoryBinding first = binding("a", original);
        RuntimeException failure = new IllegalStateException("write failed");
        HotkeyBinding broken = new HotkeyBinding() {
            int writes;
            public String id() { return "b"; }
            public InputGesture defaultGesture() { return InputGesture.mouse(2, 7, 0); }
            public InputGesture current() { return defaultGesture(); }
            public void set(InputGesture value) {
                if(writes++ == 0) throw failure;
                throw new IllegalStateException("rollback failed");
            }
            public void reset() { set(defaultGesture()); }
        };
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("a", first)); registry.register(action("b", broken));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        draft.assign("a", InputGesture.none()); draft.assign("b", InputGesture.none());
        assertSame(failure, assertThrows(IllegalStateException.class, draft::save));
        assertEquals(original, first.current());
        assertTrue(draft.isDirty());
        assertEquals(1, failure.getSuppressed().length);
    }
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

    @Test void ignoredConflictCanSaveAndStaysAcceptedForLaterSaves() {
        HotkeyRegistryTest.MemoryBinding first = binding("first", InputGesture.mouse(1, KeyMatch.MODS, 0));
        HotkeyRegistryTest.MemoryBinding second = binding("second", InputGesture.mouse(3, KeyMatch.MODS, 0));
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("first", first));
        registry.register(action("second", second));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);

        HotkeyConflict conflict = draft.assign("second", first.current()).get(0);
        draft.ignore(conflict);
        assertTrue(draft.conflicts().isEmpty());

        draft.save();
        assertEquals(first.current(), second.current());
        assertTrue(draft.conflicts().isEmpty());
        assertFalse(draft.isDirty());
        assertDoesNotThrow(draft::save);
    }

    @Test void editingOrCancellingRestoresActionableConflictAndCheckpointRestoresIgnore() {
        HotkeyRegistryTest.MemoryBinding first = binding("first", InputGesture.mouse(1, KeyMatch.MODS, 0));
        HotkeyRegistryTest.MemoryBinding second = binding("second", InputGesture.mouse(1, KeyMatch.MODS, 0));
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("first", first));
        registry.register(action("second", second));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);

        HotkeyConflict conflict = draft.conflicts().get(0);
        draft.ignore(conflict);
        HotkeyDraftModel.Checkpoint ignored = draft.checkpoint();
        draft.assign("first", first.current());
        assertTrue(draft.conflicts().isEmpty());
        draft.assign("first", InputGesture.mouse(3, KeyMatch.MODS, 0));
        draft.assign("first", first.current());
        assertFalse(draft.conflicts().isEmpty());
        draft.restore(ignored);
        assertTrue(draft.conflicts().isEmpty());

        draft.cancel();
        assertFalse(draft.conflicts().isEmpty());
    }

    @Test void replacingAnotherConflictKeepsAnUnchangedIgnoredPair() {
        InputGesture shared = InputGesture.mouse(1, KeyMatch.MODS, 0);
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("a", binding("a", shared)));
        registry.register(action("b", binding("b", InputGesture.mouse(3, KeyMatch.MODS, 0))));
        registry.register(action("c", binding("c", shared)));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        draft.ignore(draft.conflicts("a").get(0));
        draft.assign("b", shared);
        HotkeyConflict againstB = null;
        for(HotkeyConflict conflict : draft.conflicts("c"))
            if(conflict.conflictingAction().id().equals("b")) againstB = conflict;

        assertNotNull(againstB);
        draft.replace(againstB);

        assertTrue(draft.conflicts().isEmpty());
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

    @Test void stageSnapshotUsesDefaultsForMissingActionsAndRejectsWrongFamiliesAtomically() {
        HotkeyRegistryTest.MemoryBinding mouse = binding("mouse", InputGesture.mouse(1, KeyMatch.MODS, 0));
        HotkeyRegistryTest.MemoryBinding wheel = binding("wheel", InputGesture.wheel(-1, KeyMatch.MODS, 0));
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("mouse", mouse));
        registry.register(action("wheel", wheel));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        Map<String, InputGesture> values = new HashMap<>();
        values.put("mouse", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S));

        draft.stageSnapshot(values);

        assertEquals(values.get("mouse"), draft.effective("mouse"));
        assertEquals(wheel.defaultGesture(), draft.effective("wheel"));
        Map<String, InputGesture> beforeFailure = draft.effectiveSnapshot();
        values.put("mouse", InputGesture.wheel(1, KeyMatch.MODS, 0));
        assertThrows(IllegalArgumentException.class, () -> draft.stageSnapshot(values));
        assertEquals(beforeFailure, draft.effectiveSnapshot());
    }

    @Test void stagedSnapshotAppliesLateActionsAndCancelDropsTheirFallbacks() {
        HotkeyRegistry registry = new HotkeyRegistry();
        registry.register(action("known", binding("known", InputGesture.mouse(1, KeyMatch.MODS, 0))));
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        Map<String, InputGesture> values = new HashMap<>();
        values.put("late", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S));
        draft.stageSnapshot(values);
        HotkeyRegistryTest.MemoryBinding late = binding("late", InputGesture.mouse(1, KeyMatch.MODS, 0));
        registry.register(action("late", late));

        assertEquals(values.get("late"), draft.effective("late"));
        assertTrue(draft.isDirty());
        draft.save();
        assertEquals(values.get("late"), late.current());

        draft.stageSnapshot(values);
        draft.cancel();
        assertEquals(late.current(), draft.effective("late"));
    }

    @Test void lateActionIgnoresAnIncompatibleStagedGestureWithoutWriting() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyDraftModel draft = new HotkeyDraftModel(registry);
        Map<String, InputGesture> values = new HashMap<>();
        values.put("late", InputGesture.wheel(1, KeyMatch.MODS, 0));
        draft.stageSnapshot(values);
        HotkeyRegistryTest.MemoryBinding late = binding("late", InputGesture.mouse(1, KeyMatch.MODS, 0));
        registry.register(action("late", late));

        assertEquals(late.current(), draft.effective("late"));
        assertDoesNotThrow(draft::save);
        assertEquals(late.defaultGesture(), late.current());
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
