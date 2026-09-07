package nurgling.hotkeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Thread-safe ordered registry of hotkey metadata. */
public final class HotkeyRegistry {
    private final Object lock = new Object();
    private final Map<String, HotkeyAction> actions = new HashMap<>();
    private final List<Consumer<List<HotkeyAction>>> listeners = new ArrayList<>();

    public void register(HotkeyAction action) {
        if(action == null)
            throw new NullPointerException("action");
        List<Consumer<List<HotkeyAction>>> notify;
        List<HotkeyAction> state;
        synchronized(lock) {
            HotkeyAction previous = actions.get(action.id());
            if(previous != null) {
                if(!previous.metadataEquals(action))
                    throw new IllegalStateException("incompatible hotkey registration: " + action.id());
                return;
            }
            actions.put(action.id(), action);
            state = orderedSnapshotLocked();
            notify = new ArrayList<>(listeners);
        }
        for(Consumer<List<HotkeyAction>> listener : notify)
            listener.accept(state);
    }

    public List<HotkeyAction> snapshot() {
        synchronized(lock) {
            return orderedSnapshotLocked();
        }
    }

    public HotkeyAction find(String id) {
        synchronized(lock) {
            return actions.get(id);
        }
    }

    public List<HotkeyConflict> conflicts(String id, InputGesture gesture) {
        if(gesture == null)
            throw new NullPointerException("gesture");
        synchronized(lock) {
            HotkeyAction selected = actions.get(id);
            if(selected == null)
                throw new IllegalArgumentException("unknown hotkey: " + id);
            return conflictsLocked(selected, gesture, null);
        }
    }

    public List<HotkeyConflict> conflicts(HotkeyAction selected, InputGesture gesture) {
        return conflicts(selected, gesture, null);
    }

    List<HotkeyConflict> conflicts(HotkeyAction selected, InputGesture gesture,
                                   Map<String, InputGesture> effective) {
        synchronized(lock) {
            return conflictsLocked(selected, gesture, effective);
        }
    }

    private List<HotkeyConflict> conflictsLocked(HotkeyAction selected, InputGesture gesture,
                                                 Map<String, InputGesture> effective) {
        if(gesture.type() == InputGesture.Type.NONE)
            return Collections.emptyList();
        List<HotkeyConflict> result = new ArrayList<>();
        for(HotkeyAction candidate : orderedSnapshotLocked()) {
            if(candidate.id().equals(selected.id()))
                continue;
            InputGesture candidateGesture = effective == null ? candidate.binding().current() : effective.get(candidate.id());
            if(candidateGesture == null || !candidateGesture.equals(gesture) ||
                    candidateGesture.type() == InputGesture.Type.NONE)
                continue;
            if(overlaps(selected.contexts(), candidate.contexts()))
                result.add(new HotkeyConflict(selected, candidate, gesture));
        }
        return result;
    }

    private static boolean overlaps(Set<HotkeyContext> left, Set<HotkeyContext> right) {
        if(left.contains(HotkeyContext.GLOBAL) || right.contains(HotkeyContext.GLOBAL))
            return true;
        for(HotkeyContext context : left)
            if(right.contains(context))
                return true;
        return false;
    }

    public void addListener(Consumer<List<HotkeyAction>> listener) {
        if(listener == null)
            throw new NullPointerException("listener");
        synchronized(lock) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<List<HotkeyAction>> listener) {
        synchronized(lock) {
            listeners.remove(listener);
        }
    }

    private List<HotkeyAction> orderedSnapshotLocked() {
        List<HotkeyAction> result = new ArrayList<>(actions.values());
        Collections.sort(result, ACTION_ORDER);
        return Collections.unmodifiableList(result);
    }

    private static final Comparator<HotkeyAction> ACTION_ORDER = new Comparator<HotkeyAction>() {
        public int compare(HotkeyAction left, HotkeyAction right) {
            int result = Integer.compare(left.category().ordinal(), right.category().ordinal());
            if(result == 0)
                result = Integer.compare(left.order(), right.order());
            if(result == 0)
                result = left.label().compareTo(right.label());
            if(result == 0)
                result = left.id().compareTo(right.id());
            return result;
        }
    };
}
