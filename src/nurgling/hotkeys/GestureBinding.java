package nurgling.hotkeys;

public final class GestureBinding implements HotkeyBinding {
    private final String id;
    private final InputGesture defaultGesture;
    private final PreferenceStore preferences;
    private final String preferenceKey;
    private InputGesture current;

    public GestureBinding(String id, InputGesture defaultGesture, PreferenceStore preferences) {
        if(id == null || defaultGesture == null || preferences == null)
            throw new NullPointerException();
        ensureSupported(defaultGesture);
        this.id = id;
        this.defaultGesture = defaultGesture;
        this.preferences = preferences;
        this.preferenceKey = "gesturebind/" + id;
        this.current = readCurrent();
    }

    public String id() {
        return id;
    }

    public InputGesture defaultGesture() {
        return defaultGesture;
    }

    public InputGesture current() {
        return current;
    }

    public void set(InputGesture gesture) {
        if(gesture == null)
            throw new IllegalArgumentException("gesture is null");
        ensureSupported(gesture);
        preferences.set(preferenceKey, gesture.encode());
        current = gesture;
    }

    public void reset() {
        preferences.set(preferenceKey, "");
        current = defaultGesture;
    }

    private InputGesture readCurrent() {
        String encoded = preferences.get(preferenceKey, "");
        if(encoded == null || encoded.length() == 0)
            return defaultGesture;
        try {
            InputGesture gesture = InputGesture.decode(encoded);
            ensureSupported(gesture);
            return gesture;
        } catch(IllegalArgumentException e) {
            preferences.set(preferenceKey, "");
            return defaultGesture;
        }
    }

    private void ensureSupported(InputGesture gesture) {
        if(gesture.type() == InputGesture.Type.KEY)
            throw new IllegalArgumentException("gesture binding cannot store KEY gestures");
        if(defaultGesture != null && gesture.type() != InputGesture.Type.NONE &&
                gesture.type() != defaultGesture.type())
            throw new IllegalArgumentException("gesture binding must retain its input family");
    }
}
