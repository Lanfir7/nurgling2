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

    @Test void keyboardGestureCopiesMutableKeyMatchOnInputAndOutput() {
        KeyMatch source = KeyMatch.forcode(KeyEvent.VK_Q, KeyMatch.C);
        InputGesture g = InputGesture.key(source);
        InputGesture expected = InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, KeyMatch.C));
        String encoded = g.encode();

        source.code = KeyEvent.VK_F;
        source.keyname = "F";
        source.modmask = 0;
        source.modmatch = 0;
        assertEquals(expected, g);
        assertEquals(encoded, g.encode());
        assertEquals("Ctrl+Q", g.displayName());

        KeyMatch exposed = g.key();
        exposed.code = KeyEvent.VK_F;
        exposed.keyname = "F";
        exposed.modmask = 0;
        exposed.modmatch = 0;
        assertEquals(expected, g);
        assertEquals(encoded, g.encode());
        assertEquals("Ctrl+Q", g.displayName());
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
        assertThrows(IllegalArgumentException.class, () -> InputGesture.decode("k:n"));
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

    @Test void malformedModifierPayloadsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> InputGesture.decode("m:0"));
        assertThrows(IllegalArgumentException.class, () -> InputGesture.decode("m:3"));
        assertThrows(IllegalArgumentException.class, () -> InputGesture.decode("m:8"));
        assertThrows(IllegalArgumentException.class, () -> InputGesture.decode("m:broken"));
    }

    @Test void displayNamesUseStableGestureLabels() {
        assertEquals("None", InputGesture.none().displayName());
        assertEquals("Ctrl+Q", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_Q, KeyMatch.C)).displayName());
        assertEquals("Ctrl+LMB", InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C).displayName());
        assertEquals("MMB", InputGesture.mouse(2, KeyMatch.MODS, 0).displayName());
        assertEquals("RMB", InputGesture.mouse(3, KeyMatch.MODS, 0).displayName());
        assertEquals("Button 4", InputGesture.mouse(4, KeyMatch.MODS, 0).displayName());
        assertEquals("Shift+Wheel Up", InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S).displayName());
        assertEquals("Wheel Down", InputGesture.wheel(1, KeyMatch.MODS, 0).displayName());
        assertEquals("Shift", InputGesture.modifier(KeyMatch.S).displayName());
        assertEquals("Ctrl", InputGesture.modifier(KeyMatch.C).displayName());
        assertEquals("Alt", InputGesture.modifier(KeyMatch.M).displayName());
    }
}
