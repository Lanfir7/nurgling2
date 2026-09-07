package nurgling.hotkeys;

/** A requested gesture colliding with another registered action. */
public final class HotkeyConflict {
    private final HotkeyAction action;
    private final HotkeyAction conflictingAction;
    private final InputGesture gesture;

    HotkeyConflict(HotkeyAction action, HotkeyAction conflictingAction, InputGesture gesture) {
        this.action = action;
        this.conflictingAction = conflictingAction;
        this.gesture = gesture;
    }

    /** Action to which the gesture was assigned. */
    public HotkeyAction action() { return action; }
    /** Existing action that uses the same gesture in an overlapping context. */
    public HotkeyAction conflictingAction() { return conflictingAction; }
    public HotkeyAction requestedAction() { return action; }
    public HotkeyAction existingAction() { return conflictingAction; }
    public HotkeyAction other() { return conflictingAction; }
    public InputGesture gesture() { return gesture; }
    public InputGesture input() { return gesture; }
}
