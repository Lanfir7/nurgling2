package nurgling.widgets;

import haven.UI;
import haven.Widget;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.conf.NTunnelingProp;
import nurgling.conf.FontSettings;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunnelingDialogCentreLineTest {
    private NConfig previous;
    private NConfig isolated;
    private NUI ui;
    private NUI previousUI;

    @BeforeEach void setUp() throws Exception {
        previous = NConfig.current;
        previousUI = ThreadLocalUI.get();
        isolated = bareConfig();
        Field conf = NConfig.class.getDeclaredField("conf");
        conf.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<NConfig.Key, Object> values = (Map<NConfig.Key, Object>) conf.get(isolated);
        values.put(NConfig.Key.fonts, new FontSettings());
        NConfig.current = isolated;
        ui = session("alice", "chr1");
        ThreadLocalUI.set(ui);
    }

    @AfterEach void restore() {
        if (previousUI == null) ThreadLocalUI.clear();
        else ThreadLocalUI.set(previousUI);
        NConfig.current = previous;
    }

    @Test
    void characterAndAccountProfilesAreSelectedByBothKeys() throws Exception {
        NTunnelingProp aliceOne = new NTunnelingProp("alice", "chr1");
        aliceOne.centreLine = true;
        NTunnelingProp.set(aliceOne);
        NTunnelingProp.set(new NTunnelingProp("alice", "chr2"));
        NTunnelingProp bobOne = new NTunnelingProp("bob", "chr1");
        NTunnelingProp.set(bobOne);

        assertTrue(NTunnelingProp.get(ui.sessInfo).centreLine);
        ThreadLocalUI.set(session("alice", "chr2"));
        assertFalse(NTunnelingProp.get(ThreadLocalUI.get().sessInfo).centreLine);
        ThreadLocalUI.set(session("bob", "chr1"));
        assertFalse(NTunnelingProp.get(ThreadLocalUI.get().sessInfo).centreLine);
    }

    @Test
    void startSavesEditedValueAndReopeningRestoresIt() throws Exception {
        TunnelingDialog dialog = new TunnelingDialog();
        setCentreLine(dialog, true);
        invoke(dialog, "confirm");

        assertTrue(NTunnelingProp.get(ui.sessInfo).centreLine);
        TunnelingDialog reopened = new TunnelingDialog();
        assertTrue(centreLine(reopened));
        ThreadLocalUI.set(session("alice", "chr2"));
        assertFalse(centreLine(new TunnelingDialog()));
        ThreadLocalUI.set(ui);
        assertTrue(centreLine(new TunnelingDialog()));
    }

    @Test
    void cancelEscapeAndWindowCloseKeepPreviouslySavedValue() throws Exception {
        NTunnelingProp saved = new NTunnelingProp("alice", "chr1");
        saved.centreLine = true;
        NTunnelingProp.set(saved);

        TunnelingDialog cancel = new TunnelingDialog();
        setCentreLine(cancel, false);
        invoke(cancel, "cancel");
        assertTrue(NTunnelingProp.get(ui.sessInfo).centreLine);

        TunnelingDialog escape = new TunnelingDialog();
        setCentreLine(escape, false);
        escape.keydown(new Widget.KeyDownEvent(new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED,
                0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)));
        assertTrue(NTunnelingProp.get(ui.sessInfo).centreLine);

        TunnelingDialog close = new TunnelingDialog();
        setCentreLine(close, false);
        close.wdgmsg("close");
        assertTrue(NTunnelingProp.get(ui.sessInfo).centreLine);
    }

    private static NUI session(String username, String chrid) throws Exception {
        Unsafe unsafe = unsafe();
        NUI session = (NUI) unsafe.allocateInstance(NUI.class);
        session.sessInfo = session.new NSessInfo(username);
        NCharacterInfo character = (NCharacterInfo) unsafe.allocateInstance(NCharacterInfo.class);
        character.chrid = chrid;
        session.sessInfo.characterInfo = character;
        NGameUI game = (NGameUI) unsafe.allocateInstance(NGameUI.class);
        game.ui = session;
        session.gui = game;
        return session;
    }

    private static NConfig bareConfig() throws Exception {
        Unsafe unsafe = unsafe();
        NConfig config = (NConfig) unsafe.allocateInstance(NConfig.class);
        Field conf = NConfig.class.getDeclaredField("conf");
        conf.setAccessible(true);
        java.lang.reflect.Constructor<?> constructor = Class.forName("nurgling.NConfig$ConfMap").getDeclaredConstructor();
        constructor.setAccessible(true);
        conf.set(config, constructor.newInstance());
        return config;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setCentreLine(TunnelingDialog dialog, boolean value) throws Exception {
        Field field = TunnelingDialog.class.getDeclaredField("centreLine");
        field.setAccessible(true);
        field.set(dialog, value);
    }

    private static boolean centreLine(TunnelingDialog dialog) throws Exception {
        Field field = TunnelingDialog.class.getDeclaredField("centreLine");
        field.setAccessible(true);
        return field.getBoolean(dialog);
    }

    private static void invoke(TunnelingDialog dialog, String method) throws Exception {
        Method action = TunnelingDialog.class.getDeclaredMethod(method);
        action.setAccessible(true);
        action.invoke(dialog);
    }
}
