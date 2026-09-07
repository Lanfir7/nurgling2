package nurgling.hotkeys.presets;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyDraftModel;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.hotkeys.InputGesture;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Commits runtime bindings and preset persistence as one logical transaction. */
public final class HotkeyPresetSaveCoordinator {
    private final HotkeyRegistry registry;
    private final HotkeyDraftModel hotkeys;
    private final HotkeyPresetDraftModel presets;
    private final HotkeyPresetRepository repository;

    public HotkeyPresetSaveCoordinator(HotkeyRegistry registry, HotkeyDraftModel hotkeys,
                                       HotkeyPresetDraftModel presets,
                                       HotkeyPresetRepository repository) {
        if(registry == null || hotkeys == null || presets == null || repository == null)
            throw new NullPointerException();
        this.registry = registry;
        this.hotkeys = hotkeys;
        this.presets = presets;
        this.repository = repository;
    }

    public void save() {
        if(!hotkeys.conflicts().isEmpty())
            throw new IllegalStateException("hotkey conflicts must be resolved before save");
        Map<String, InputGesture> runtimeBefore = runtimeSnapshot();
        HotkeyDraftModel.Checkpoint hotkeysBefore = hotkeys.checkpoint();
        HotkeyPresetDraftModel.Checkpoint presetsBefore = presets.checkpoint();
        HotkeyPresetRepository.Checkpoint storeBefore;
        try {
            storeBefore = repository.checkpoint();
        } catch(IOException failure) {
            throw new RuntimeException("unable to snapshot hotkey presets", failure);
        }

        boolean storeAttempted = false;
        try {
            hotkeys.save();
            storeAttempted = true;
            repository.save(presets.persistentState());
            presets.markSaved();
        } catch(Exception failure) {
            RuntimeException result = new RuntimeException("unable to save hotkey presets", failure);
            restoreRuntime(runtimeBefore, result);
            if(storeAttempted) restoreStore(storeBefore, result);
            hotkeys.restore(hotkeysBefore);
            presets.restore(presetsBefore);
            throw result;
        }
    }

    public void cancel() {
        hotkeys.cancel();
        presets.restoreSavedState();
    }

    private Map<String, InputGesture> runtimeSnapshot() {
        Map<String, InputGesture> result = new LinkedHashMap<>();
        for(HotkeyAction action : registry.snapshot()) result.put(action.id(), action.current());
        return result;
    }

    private void restoreRuntime(Map<String, InputGesture> snapshot, RuntimeException failure) {
        for(HotkeyAction action : registry.snapshot()) {
            InputGesture original = snapshot.get(action.id());
            if(original == null) continue;
            try {
                action.binding().set(original);
            } catch(RuntimeException rollbackFailure) {
                if(rollbackFailure != failure) failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private void restoreStore(HotkeyPresetRepository.Checkpoint checkpoint, RuntimeException failure) {
        try {
            repository.restore(checkpoint);
        } catch(Exception rollbackFailure) {
            if(rollbackFailure != failure) failure.addSuppressed(rollbackFailure);
        }
    }
}
