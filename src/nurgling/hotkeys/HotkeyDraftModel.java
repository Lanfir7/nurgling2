package nurgling.hotkeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Staged, conflict-aware edits for the hotkey settings page. */
public final class HotkeyDraftModel {
    private enum ChangeKind { SET, RESET }
    private static final class Change {
        final ChangeKind kind;
        final InputGesture value;
        Change(ChangeKind kind, InputGesture value) { this.kind = kind; this.value = value; }
    }

    private final HotkeyRegistry registry;
    private final Map<String, Change> changes = new LinkedHashMap<>();

    /** Immutable point-in-time copy of staged operations for a cancelable dialog. */
    public static final class Checkpoint {
        private final Map<String, Change> changes;
        private Checkpoint(Map<String, Change> changes) {
            this.changes = changes;
        }
    }

    public HotkeyDraftModel(HotkeyRegistry registry) {
        if(registry == null)
            throw new NullPointerException("registry");
        this.registry = registry;
    }

    public List<HotkeyConflict> assign(String id, InputGesture gesture) {
        HotkeyAction action = requireAction(id);
        if(gesture == null)
            throw new NullPointerException("gesture");
        if(!action.allows(gesture.type()))
            throw new IllegalArgumentException("gesture type is not allowed for " + id);
        changes.put(id, new Change(ChangeKind.SET, gesture));
        return conflicts(id);
    }

    public void reset(String id) {
        requireAction(id);
        changes.put(id, new Change(ChangeKind.RESET, null));
    }

    public void resetCategory(HotkeyCategory category) {
        if(category == null)
            throw new NullPointerException("category");
        for(HotkeyAction action : registry.snapshot())
            if(category == HotkeyCategory.ALL || action.category() == category)
                reset(action.id());
    }

    public void resetAll() {
        for(HotkeyAction action : registry.snapshot())
            reset(action.id());
    }

    public InputGesture effective(String id) {
        HotkeyAction action = requireAction(id);
        Change change = changes.get(id);
        if(change == null)
            return action.binding().current();
        return change.kind == ChangeKind.RESET ? action.binding().defaultGesture() : change.value;
    }

    public List<HotkeyConflict> conflicts(String id) {
        HotkeyAction selected = requireAction(id);
        InputGesture requested = effective(id);
        if(requested.type() == InputGesture.Type.NONE)
            return Collections.emptyList();
        List<HotkeyConflict> result = new ArrayList<>();
        for(HotkeyAction candidate : registry.snapshot()) {
            if(candidate.id().equals(id))
                continue;
            InputGesture other = effective(candidate.id());
            if(other.overlaps(requested) && other.type() != InputGesture.Type.NONE &&
                    selected.overlapsContext(candidate) && !selected.sharesDefaultWith(candidate, requested, other))
                result.add(new HotkeyConflict(selected, candidate, requested));
        }
        return result;
    }

    public List<HotkeyConflict> conflicts() {
        List<HotkeyConflict> result = new ArrayList<>();
        List<HotkeyAction> actions = registry.snapshot();
        for(HotkeyAction action : actions) {
            for(HotkeyConflict conflict : conflicts(action.id())) {
                boolean reverse = false;
                for(HotkeyConflict existing : result)
                    if(existing.action().id().equals(conflict.conflictingAction().id()) &&
                            existing.conflictingAction().id().equals(action.id())) {
                        reverse = true;
                        break;
                    }
                if(!reverse)
                    result.add(conflict);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void replace(HotkeyConflict conflict) {
        if(conflict == null)
            throw new NullPointerException("conflict");
        assign(conflict.action().id(), conflict.gesture());
        changes.put(conflict.conflictingAction().id(), new Change(ChangeKind.SET, InputGesture.none()));
    }

    public void save() {
        List<HotkeyConflict> conflicts = conflicts();
        if(!conflicts.isEmpty())
            throw new IllegalStateException("hotkey conflicts must be resolved before save");
        List<Map.Entry<String, Change>> operations = new ArrayList<>();
        for(HotkeyAction action : registry.snapshot()) {
            Change change = changes.get(action.id());
            if(change != null)
                operations.add(new java.util.AbstractMap.SimpleImmutableEntry<>(action.id(), change));
        }
        Map<String, InputGesture> original = new LinkedHashMap<>();
        for(Map.Entry<String, Change> entry : operations)
            original.put(entry.getKey(), requireAction(entry.getKey()).binding().current());
        try {
            for(Map.Entry<String, Change> entry : operations) {
                HotkeyAction action = requireAction(entry.getKey());
                if(entry.getValue().kind == ChangeKind.RESET)
                    action.binding().reset();
                else
                    action.binding().set(entry.getValue().value);
            }
        } catch(RuntimeException failure) {
            for(Map.Entry<String, InputGesture> entry : original.entrySet()) {
                try {
                    requireAction(entry.getKey()).binding().set(entry.getValue());
                } catch(RuntimeException rollbackFailure) {
                    if(rollbackFailure != failure) failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
        changes.clear();
    }

    public void cancel() {
        changes.clear();
    }

    public boolean isDirty() { return !changes.isEmpty(); }

    public Checkpoint checkpoint() {
        return new Checkpoint(new LinkedHashMap<>(changes));
    }

    public void restore(Checkpoint checkpoint) {
        if(checkpoint == null)
            throw new NullPointerException("checkpoint");
        changes.clear();
        changes.putAll(checkpoint.changes);
    }

    private HotkeyAction requireAction(String id) {
        HotkeyAction action = registry.find(id);
        if(action == null)
            throw new IllegalArgumentException("unknown hotkey: " + id);
        return action;
    }

}
