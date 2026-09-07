package nurgling.hotkeys;

import haven.KeyBinding;
import haven.KeyMatch;

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
        return InputGesture.key(binding.defkey);
    }

    public InputGesture current() {
        return InputGesture.key(binding.key());
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
}
