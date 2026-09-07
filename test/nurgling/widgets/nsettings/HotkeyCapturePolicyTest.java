package nurgling.widgets.nsettings;

import haven.KeyMatch;
import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Set;

import static nurgling.widgets.nsettings.HotkeyCapturePolicy.*;
import static nurgling.hotkeys.InputGesture.Type.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HotkeyCapturePolicyTest {
    @Test void captureCommandsAndInputFamiliesAreDeterministic() {
        assertEquals(CANCEL, HotkeyCapturePolicy.key(KeyEvent.VK_ESCAPE, allowed(MOUSE_BUTTON)).kind);
        assertEquals(RESET, HotkeyCapturePolicy.key(KeyEvent.VK_BACK_SPACE, allowed(MOUSE_BUTTON)).kind);
        assertEquals(DISABLE, HotkeyCapturePolicy.key(KeyEvent.VK_DELETE, allowed(MOUSE_BUTTON)).kind);
        assertEquals(REJECT_TYPE, HotkeyCapturePolicy.key(KeyEvent.VK_Q, allowed(MOUSE_BUTTON)).kind);
        assertEquals(ASSIGN, HotkeyCapturePolicy.mouse(3, KeyMatch.C, allowed(MOUSE_BUTTON)).kind);
        assertEquals(ASSIGN, HotkeyCapturePolicy.key(KeyEvent.VK_CONTROL, allowed(MODIFIER)).kind);
        assertEquals(IGNORE_MODIFIER,
                HotkeyCapturePolicy.key(KeyEvent.VK_CONTROL, allowed(MOUSE_BUTTON)).kind);
    }

    private static Set<InputGesture.Type> allowed(InputGesture.Type type) {
        return EnumSet.of(type);
    }
}
