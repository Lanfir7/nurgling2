package nurgling.hotkeys;

import haven.KeyMatch;
import haven.Widget;
import nurgling.NGameUI;
import nurgling.widgets.BotsInterruptWidget;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class SessionMacroStopHotkeyTest {
    private nurgling.NConfig previousConfig;
    private nurgling.ClientResourceFixture resources;

    @org.junit.jupiter.api.BeforeEach void initializeClientFonts() throws Exception {
        resources = new nurgling.ClientResourceFixture();
        previousConfig = nurgling.NConfig.current;
        if (previousConfig == null)
            nurgling.NConfig.current = new nurgling.NConfig();
    }

    @org.junit.jupiter.api.AfterEach void restoreConfig() throws Exception {
        nurgling.NConfig.current = previousConfig;
        if (resources != null) resources.close();
    }

    @Test void stopShortcutBelongsToSessionsAndHasNoDefaultConflict() {
        HotkeyRegistry registry = new HotkeyRegistry();
        HotkeyCatalog.registerCore(registry);
        HotkeyAction action = registry.find(Hotkeys.SESSION_STOP_MACROS);
        assertNotNull(action);
        assertEquals(HotkeyCategory.SESSIONS, action.category());
        assertTrue(action.contexts().contains(HotkeyContext.GLOBAL));
        assertTrue(action.defaultGesture().matches(key(KeyEvent.VK_X,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), 0));
        for (HotkeyAction other : registry.snapshot()) {
            if (!other.id().equals(action.id()))
                assertFalse(other.defaultGesture().matches(key(KeyEvent.VK_X,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), 0), other.id());
        }
    }

    @Test void reboundShortcutStopsOnlyTheReceivingGameSession() throws Exception {
        HotkeyAction action = Hotkeys.action(Hotkeys.SESSION_STOP_MACROS);
        InputGesture previous = action.current();
        try {
            action.binding().set(InputGesture.key(KeyMatch.forcode(KeyEvent.VK_F8, KeyMatch.C)));
            NGameUI first = allocate(NGameUI.class);
            NGameUI second = allocate(NGameUI.class);
            RecordingMacros firstMacros = allocate(RecordingMacros.class);
            RecordingMacros secondMacros = allocate(RecordingMacros.class);
            first.biw = firstMacros;
            second.biw = secondMacros;

            assertFalse(Hotkeys.matchesKey(Hotkeys.SESSION_STOP_MACROS,
                    key(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
            assertTrue(first.globtype(new Widget.GlobKeyEvent(key(KeyEvent.VK_F8, InputEvent.CTRL_DOWN_MASK))));
            assertEquals(1, firstMacros.stops);
            assertEquals(0, secondMacros.stops);
            assertTrue(second.globtype(new Widget.GlobKeyEvent(key(KeyEvent.VK_F8, InputEvent.CTRL_DOWN_MASK))));
            assertEquals(1, firstMacros.stops);
            assertEquals(1, secondMacros.stops);
        } finally {
            action.binding().set(previous);
        }
    }

    @Test void matchingShortcutIsSafeBeforeMacroRegistryIsAttached() throws Exception {
        HotkeyAction action = Hotkeys.action(Hotkeys.SESSION_STOP_MACROS);
        InputGesture previous = action.current();
        try {
            action.binding().set(action.defaultGesture());
            NGameUI gui = allocate(NGameUI.class);
            assertTrue(gui.globtype(new Widget.GlobKeyEvent(key(KeyEvent.VK_X,
                    InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))));
        } finally {
            action.binding().set(previous);
        }
    }

    private static KeyEvent key(int code, int modifiers) {
        return new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static class RecordingMacros extends BotsInterruptWidget {
        int stops;

        @Override public void interruptAll() {
            stops++;
        }
    }
}
