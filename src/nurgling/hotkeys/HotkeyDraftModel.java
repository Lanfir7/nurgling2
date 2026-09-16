package nurgling.hotkeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Staged, conflict-aware edits for the hotkey settings page. */
public final class HotkeyDraftModel {
    private enum ChangeKind { SET, RESET }
    private static final class Change {
        final ChangeKind kind;
        final InputGesture value;
        Change(ChangeKind kind, InputGesture value) { this.kind = kind; this.value = value; }
    }

    /** An explicitly accepted overlapping pair, tied to its effective gestures. */
    private static final class IgnoredConflict {
        final String firstActionId;
        final String secondActionId;
        final InputGesture firstGesture;
        final InputGesture secondGesture;

        private IgnoredConflict(String firstActionId, String secondActionId,
                                InputGesture firstGesture, InputGesture secondGesture) {
            this.firstActionId = firstActionId;
            this.secondActionId = secondActionId;
            this.firstGesture = firstGesture;
            this.secondGesture = secondGesture;
        }

        static IgnoredConflict of(HotkeyConflict conflict, InputGesture otherGesture) {
            String actionId = conflict.action().id();
            String otherId = conflict.conflictingAction().id();
            if(actionId.compareTo(otherId) <= 0)
                return new IgnoredConflict(actionId, otherId, conflict.gesture(), otherGesture);
            return new IgnoredConflict(otherId, actionId, otherGesture, conflict.gesture());
        }

        boolean includes(String actionId) {
            return firstActionId.equals(actionId) || secondActionId.equals(actionId);
        }

        @Override
        public boolean equals(Object other) {
            if(this == other) return true;
            if(!(other instanceof IgnoredConflict)) return false;
            IgnoredConflict that = (IgnoredConflict)other;
            return firstActionId.equals(that.firstActionId) && secondActionId.equals(that.secondActionId) &&
                    firstGesture.equals(that.firstGesture) && secondGesture.equals(that.secondGesture);
        }

        @Override
        public int hashCode() {
            return Objects.hash(firstActionId, secondActionId, firstGesture, secondGesture);
        }
    }

    private final HotkeyRegistry registry;
    private final Map<String, Change> changes = new LinkedHashMap<>();
    private Map<String, InputGesture> stagedSnapshot;
    private Set<IgnoredConflict> ignoredConflicts = new HashSet<>();
    private Set<IgnoredConflict> savedIgnoredConflicts = new HashSet<>();

    /** Immutable point-in-time copy of staged operations for a cancelable dialog. */
    public static final class Checkpoint {
        private final Map<String, Change> changes;
        private final Map<String, InputGesture> stagedSnapshot;
        private final Set<IgnoredConflict> ignoredConflicts;
        private final Set<IgnoredConflict> savedIgnoredConflicts;
        private Checkpoint(Map<String, Change> changes, Map<String, InputGesture> stagedSnapshot,
                           Set<IgnoredConflict> ignoredConflicts,
                           Set<IgnoredConflict> savedIgnoredConflicts) {
            this.changes = changes;
            this.stagedSnapshot = stagedSnapshot;
            this.ignoredConflicts = ignoredConflicts;
            this.savedIgnoredConflicts = savedIgnoredConflicts;
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
        if(!gesture.equals(effective(id))) invalidateIgnoredConflicts(id);
        changes.put(id, new Change(ChangeKind.SET, gesture));
        return conflicts(id);
    }

    public void reset(String id) {
        HotkeyAction action = requireAction(id);
        if(!action.defaultGesture().equals(effective(id))) invalidateIgnoredConflicts(id);
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
        if(change != null)
            return change.kind == ChangeKind.RESET ? action.binding().defaultGesture() : change.value;
        if(stagedSnapshot == null)
            return action.binding().current();
        InputGesture value = stagedSnapshot.get(id);
        if(value == null) value = action.binding().defaultGesture();
        if(!action.allows(value.type()))
            return action.binding().current();
        return value;
    }

    public Map<String, InputGesture> effectiveSnapshot() {
        Map<String, InputGesture> values = new TreeMap<>();
        for(HotkeyAction action : registry.snapshot())
            values.put(action.id(), effective(action.id()));
        return Collections.unmodifiableMap(values);
    }

    /** Replaces the whole draft atomically; values for later-registered actions are retained. */
    public void stageSnapshot(Map<String, InputGesture> values) {
        if(values == null) throw new NullPointerException("values");
        Map<String, InputGesture> snapshot = new TreeMap<>();
        for(Map.Entry<String, InputGesture> entry : values.entrySet()) {
            if(entry.getKey() == null || entry.getValue() == null)
                throw new IllegalArgumentException("invalid hotkey snapshot");
            snapshot.put(entry.getKey(), entry.getValue());
        }
        Map<String, Change> replacement = new LinkedHashMap<>();
        Set<String> changedActions = new HashSet<>();
        for(HotkeyAction action : registry.snapshot()) {
            InputGesture value = snapshot.get(action.id());
            if(value == null) value = action.defaultGesture();
            if(!action.allows(value.type()))
                throw new IllegalArgumentException("gesture type is not allowed for " + action.id());
            if(!value.equals(effective(action.id()))) changedActions.add(action.id());
            if(value.equals(action.current()))
                continue;
            replacement.put(action.id(), value.equals(action.defaultGesture())
                    ? new Change(ChangeKind.RESET, null) : new Change(ChangeKind.SET, value));
        }
        changes.clear();
        changes.putAll(replacement);
        stagedSnapshot = snapshot;
        for(String actionId : changedActions) invalidateIgnoredConflicts(actionId);
    }

    public List<HotkeyConflict> conflicts(String id) {
        return conflicts(id, true);
    }

    private List<HotkeyConflict> conflicts(String id, boolean excludeIgnored) {
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
                    selected.overlapsContext(candidate) && !selected.sharesDefaultWith(candidate, requested, other)) {
                HotkeyConflict conflict = new HotkeyConflict(selected, candidate, requested);
                if(!excludeIgnored || !isIgnored(conflict)) result.add(conflict);
            }
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
        String otherId = conflict.conflictingAction().id();
        if(!InputGesture.none().equals(effective(otherId))) invalidateIgnoredConflicts(otherId);
        changes.put(otherId, new Change(ChangeKind.SET, InputGesture.none()));
    }

    /** Accept the current overlap until either action's effective gesture changes. */
    public void ignore(HotkeyConflict conflict) {
        if(conflict == null) throw new NullPointerException("conflict");
        String actionId = conflict.action().id();
        String otherId = conflict.conflictingAction().id();
        requireAction(actionId);
        requireAction(otherId);
        boolean present = false;
        for(HotkeyConflict candidate : conflicts(actionId, false)) {
            if(candidate.conflictingAction().id().equals(otherId) &&
                    candidate.gesture().equals(conflict.gesture())) {
                present = true;
                break;
            }
        }
        if(!present) throw new IllegalArgumentException("conflict is no longer active");
        ignoredConflicts.add(IgnoredConflict.of(conflict, effective(otherId)));
    }

    public void save() {
        List<HotkeyConflict> conflicts = conflicts();
        if(!conflicts.isEmpty())
            throw new IllegalStateException("hotkey conflicts must be resolved before save");
        List<Map.Entry<String, Change>> operations = new ArrayList<>();
        for(HotkeyAction action : registry.snapshot()) {
            Change change = changes.get(action.id());
            if(change != null) {
                operations.add(new java.util.AbstractMap.SimpleImmutableEntry<>(action.id(), change));
                continue;
            }
            if(stagedSnapshot == null) continue;
            InputGesture value = effective(action.id());
            if(value.equals(action.current())) continue;
            Change fallbackChange = value.equals(action.defaultGesture())
                    ? new Change(ChangeKind.RESET, null) : new Change(ChangeKind.SET, value);
            operations.add(new java.util.AbstractMap.SimpleImmutableEntry<>(action.id(), fallbackChange));
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
        stagedSnapshot = null;
        savedIgnoredConflicts = new HashSet<>(ignoredConflicts);
    }

    public void cancel() {
        changes.clear();
        stagedSnapshot = null;
        ignoredConflicts = new HashSet<>(savedIgnoredConflicts);
    }

    public boolean isDirty() {
        if(!changes.isEmpty() || !ignoredConflicts.equals(savedIgnoredConflicts)) return true;
        if(stagedSnapshot == null) return false;
        for(HotkeyAction action : registry.snapshot())
            if(!effective(action.id()).equals(action.current())) return true;
        return false;
    }

    public Checkpoint checkpoint() {
        return new Checkpoint(new LinkedHashMap<>(changes), stagedSnapshot == null ? null :
                new TreeMap<>(stagedSnapshot), new HashSet<>(ignoredConflicts),
                new HashSet<>(savedIgnoredConflicts));
    }

    public void restore(Checkpoint checkpoint) {
        if(checkpoint == null)
            throw new NullPointerException("checkpoint");
        changes.clear();
        changes.putAll(checkpoint.changes);
        stagedSnapshot = checkpoint.stagedSnapshot == null ? null : new TreeMap<>(checkpoint.stagedSnapshot);
        ignoredConflicts = new HashSet<>(checkpoint.ignoredConflicts);
        savedIgnoredConflicts = new HashSet<>(checkpoint.savedIgnoredConflicts);
    }

    private boolean isIgnored(HotkeyConflict conflict) {
        return ignoredConflicts.contains(IgnoredConflict.of(conflict,
                effective(conflict.conflictingAction().id())));
    }

    private void invalidateIgnoredConflicts(String actionId) {
        ignoredConflicts.removeIf(value -> value.includes(actionId));
    }

    private HotkeyAction requireAction(String id) {
        HotkeyAction action = registry.find(id);
        if(action == null)
            throw new IllegalArgumentException("unknown hotkey: " + id);
        return action;
    }

}
