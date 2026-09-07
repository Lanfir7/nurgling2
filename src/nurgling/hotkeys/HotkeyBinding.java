package nurgling.hotkeys;

public interface HotkeyBinding {
    String id();
    InputGesture defaultGesture();
    InputGesture current();
    void set(InputGesture gesture);
    void reset();

    /** Captures persistence semantics as well as the effective gesture. */
    default Object checkpoint() { return current(); }

    default void restore(Object checkpoint) {
        InputGesture gesture = (InputGesture)checkpoint;
        if(defaultGesture().equals(gesture)) reset();
        else set(gesture);
    }
}
