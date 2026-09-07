package nurgling.widgets.nsettings;

import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.InputGesture;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.Set;

/** Deterministic interpretation of events received by a hotkey capture control. */
public final class HotkeyCapturePolicy {
    public enum Kind { CANCEL, RESET, DISABLE, ASSIGN, REJECT_TYPE, IGNORE_MODIFIER }
    public static final Kind CANCEL = Kind.CANCEL;
    public static final Kind RESET = Kind.RESET;
    public static final Kind DISABLE = Kind.DISABLE;
    public static final Kind ASSIGN = Kind.ASSIGN;
    public static final Kind REJECT_TYPE = Kind.REJECT_TYPE;
    public static final Kind IGNORE_MODIFIER = Kind.IGNORE_MODIFIER;

    public static final class Decision {
        public final Kind kind;
        public final InputGesture gesture;

        private Decision(Kind kind, InputGesture gesture) {
            this.kind = kind;
            this.gesture = gesture;
        }

        public Kind kind() { return kind; }
        public InputGesture gesture() { return gesture; }
    }

    private HotkeyCapturePolicy() { }

    public static Decision key(int keyCode, Set<InputGesture.Type> allowed) {
        Set<InputGesture.Type> types = allowed == null ? Collections.<InputGesture.Type>emptySet() : allowed;
        if(keyCode == KeyEvent.VK_ESCAPE)
            return new Decision(CANCEL, null);
        if(keyCode == KeyEvent.VK_BACK_SPACE)
            return new Decision(RESET, null);
        if(keyCode == KeyEvent.VK_DELETE)
            return new Decision(DISABLE, null);
        int modifier = modifier(keyCode);
        if(modifier != 0) {
            if(!types.contains(InputGesture.Type.MODIFIER))
                return new Decision(IGNORE_MODIFIER, null);
            return new Decision(ASSIGN, InputGesture.modifier(modifier));
        }
        if(!types.contains(InputGesture.Type.KEY))
            return new Decision(REJECT_TYPE, null);
        return new Decision(ASSIGN, InputGesture.key(KeyMatch.forcode(keyCode, 0)));
    }

    public static Decision key(int keyCode, HotkeyAction action) {
        if(action == null)
            throw new NullPointerException("action");
        return key(keyCode, action.allowedTypes());
    }

    public static Decision mouse(int button, int modifiers, Set<InputGesture.Type> allowed) {
        Set<InputGesture.Type> types = allowed == null ? Collections.<InputGesture.Type>emptySet() : allowed;
        if(!types.contains(InputGesture.Type.MOUSE_BUTTON))
            return new Decision(REJECT_TYPE, null);
        return new Decision(ASSIGN, InputGesture.mouse(button, KeyMatch.MODS, modifiers));
    }

    public static Decision mouse(int button, int modifiers, HotkeyAction action) {
        if(action == null)
            throw new NullPointerException("action");
        return mouse(button, modifiers, action.allowedTypes());
    }

    private static int modifier(int keyCode) {
        switch(keyCode) {
        case KeyEvent.VK_SHIFT: return KeyMatch.S;
        case KeyEvent.VK_CONTROL: return KeyMatch.C;
        case KeyEvent.VK_ALT: return KeyMatch.M;
        default: return 0;
        }
    }
}
