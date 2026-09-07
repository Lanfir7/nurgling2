package nurgling.hotkeys.presets;

import nurgling.hotkeys.InputGesture;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, complete hotkey snapshot. */
public final class HotkeyPreset {
    private final String id;
    private final String name;
    private final boolean builtIn;
    private final Map<String, InputGesture> gestures;

    public HotkeyPreset(String id, String name, boolean builtIn,
                        Map<String, InputGesture> gestures) {
        if(id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty() || gestures == null)
            throw new IllegalArgumentException("invalid preset");
        TreeMap<String, InputGesture> copy = new TreeMap<>();
        for(Map.Entry<String, InputGesture> entry : gestures.entrySet()) {
            if(entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null)
                throw new IllegalArgumentException("invalid preset binding");
            copy.put(entry.getKey(), entry.getValue());
        }
        this.id = id;
        this.name = name.trim();
        this.builtIn = builtIn;
        this.gestures = Collections.unmodifiableMap(copy);
    }

    public String id() { return id; }
    public String name() { return name; }
    public boolean builtIn() { return builtIn; }
    public Map<String, InputGesture> gestures() { return gestures; }
    public InputGesture gesture(String actionId) { return gestures.get(actionId); }
    public HotkeyPreset renamed(String value) { return new HotkeyPreset(id, value, builtIn, gestures); }

    @Override
    public boolean equals(Object other) {
        if(this == other) return true;
        if(!(other instanceof HotkeyPreset)) return false;
        HotkeyPreset that = (HotkeyPreset)other;
        return builtIn == that.builtIn && id.equals(that.id) && name.equals(that.name) &&
                gestures.equals(that.gestures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, builtIn, gestures);
    }
}
