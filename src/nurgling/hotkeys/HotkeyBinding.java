package nurgling.hotkeys;

public interface HotkeyBinding {
    String id();
    InputGesture defaultGesture();
    InputGesture current();
    void set(InputGesture gesture);
    void reset();
}
