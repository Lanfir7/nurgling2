package nurgling.hotkeys;

import haven.KeyBinding;
import haven.KeyMatch;
import haven.Utils;

public final class KeyBindingHotkey implements HotkeyBinding {
    private final KeyBinding binding;

    public KeyBindingHotkey(KeyBinding binding) {
        if(binding == null)
            throw new NullPointerException("binding");
        this.binding = binding;
    }

    public String id() {
        return binding.id;
    }

    public InputGesture defaultGesture() {
        return effective(binding.defkey);
    }

    public InputGesture current() {
        return effective(binding.key());
    }

    private InputGesture effective(KeyMatch key) {
        InputGesture gesture = InputGesture.key(key);
        if(gesture.type() != InputGesture.Type.KEY) return gesture;
        KeyMatch effective = gesture.key();
        effective.modmask &= ~binding.modign;
        effective.modmatch &= ~binding.modign;
        return InputGesture.key(effective);
    }

    public void set(InputGesture gesture) {
        if(gesture == null)
            throw new IllegalArgumentException("gesture is null");
        switch(gesture.type()) {
        case NONE:
            binding.set(KeyMatch.nil);
            break;
        case KEY:
            binding.set(gesture.key());
            break;
        default:
            throw new IllegalArgumentException("legacy key binding cannot store " + gesture.type());
        }
    }

    public void reset() {
        binding.set(null);
    }

    @Override
    public Object checkpoint() {
        String encoded = Utils.getpref("keybind/" + binding.id, null);
        return new Snapshot(encoded != null, encoded, binding.key);
    }

    @Override
    public void restore(Object checkpoint) {
        if(!(checkpoint instanceof Snapshot))
            throw new IllegalArgumentException("foreign key binding checkpoint");
        Snapshot saved = (Snapshot)checkpoint;
        Utils.setpref("keybind/" + binding.id, saved.existed ? saved.encoded : null);
        binding.key = saved.key;
    }

    private static final class Snapshot {
        final boolean existed;
        final String encoded;
        final KeyMatch key;
        Snapshot(boolean existed, String encoded, KeyMatch key) {
            this.existed = existed;
            this.encoded = encoded;
            this.key = key;
        }
    }
}
