package nurgling.hotkeys;

import haven.KeyMatch;
import org.junit.jupiter.api.Test;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import static org.junit.jupiter.api.Assertions.*;

class InputGestureTest {
    @Test void mouseGestureRequiresButtonAndMaskedModifiers() {
        InputGesture g = InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.S);
        assertTrue(g.matchesMouse(1, KeyMatch.S));
        assertFalse(g.matchesMouse(3, KeyMatch.S));
        assertFalse(g.matchesMouse(1, KeyMatch.S | KeyMatch.C));
    }

    @Test void wheelGestureStoresDirectionNotMagnitude() {
        InputGesture up = InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S);
        assertTrue(up.matchesWheel(-4, KeyMatch.S));
        assertFalse(up.matchesWheel(2, KeyMatch.S));
    }

    @Test void keyboardGestureDelegatesLegacyKeyMatchRules() {
        InputGesture g = InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, KeyMatch.C));
        KeyEvent q = new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 1,
                KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_Q, 'Q');
        assertTrue(g.matches(q, 0));
    }

    @Test void everyGestureRoundTripsAndCorruptionIsRejected() {
        InputGesture[] values = {
                InputGesture.none(),
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F, KeyMatch.C | KeyMatch.S)),
                InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C),
                InputGesture.wheel(1, KeyMatch.MODS, KeyMatch.S),
                InputGesture.modifier(KeyMatch.C)
        };
        for(InputGesture value : values)
            assertEquals(value, InputGesture.decode(value.encode()));
        assertThrows(IllegalArgumentException.class, () -> InputGesture.decode("b:broken"));
    }

    @Test void modifierModeRequiresExactlyOneHeldModifier() {
        InputGesture ctrl = InputGesture.modifier(KeyMatch.C);
        assertTrue(ctrl.matchesModifiers(KeyMatch.C));
        assertFalse(ctrl.matchesModifiers(KeyMatch.C | KeyMatch.S));
        assertThrows(IllegalArgumentException.class,
                () -> InputGesture.modifier(KeyMatch.C | KeyMatch.S));
        assertThrows(IllegalArgumentException.class,
                () -> InputGesture.modifier(1 << 12));
    }
}
