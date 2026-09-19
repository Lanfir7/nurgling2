package haven;

import nurgling.ClientResourceFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class FightsessCombatHotkeyPriorityTest {
    private ClientResourceFixture resources;

    @BeforeEach void loadUiAssets() throws Exception {
        resources = new ClientResourceFixture();
    }

    @AfterEach void restoreResources() throws Exception {
        if(resources != null)
            resources.close();
    }

    @Test void defaultNumberKeysBelongToCombatMoves() {
        assertTrue(Fightsess.isCombatActionKey(glob(KeyEvent.VK_1)));
        assertTrue(Fightsess.isCombatActionKey(glob(KeyEvent.VK_5)));
        assertFalse(Fightsess.isCombatActionKey(glob(KeyEvent.VK_Q)));
    }

    @Test void beltYieldsNumberKeysOnlyWhileCombatSessionIsAttached() throws Exception {
        Fightsess sess = allocate(Fightsess.class);
        assertFalse(Fightsess.capturesBeltKey(null, glob(KeyEvent.VK_1)));
        assertFalse(Fightsess.capturesBeltKey(sess, glob(KeyEvent.VK_1)));

        Widget host = new Widget();
        sess.parent = host;
        assertTrue(Fightsess.capturesBeltKey(sess, glob(KeyEvent.VK_1)));
        assertFalse(Fightsess.capturesBeltKey(sess, glob(KeyEvent.VK_Q)));

        sess.parent = null;
        assertFalse(Fightsess.capturesBeltKey(sess, glob(KeyEvent.VK_1)));
    }

    @Test void laterBeltDoesNotStealCombatKeysWhenItYields() throws Exception {
        Widget root = new Widget();
        RecordingWidget combat = new RecordingWidget();
        RecordingBelt belt = new RecordingBelt();
        root.add(combat);
        root.add(belt);

        Fightsess sess = allocate(Fightsess.class);
        sess.parent = root;
        belt.session = sess;

        assertTrue(new Widget.GlobKeyEvent(key(KeyEvent.VK_1)).dispatch(root));
        assertTrue(combat.used);
        assertFalse(belt.used);
    }

    private static Widget.GlobKeyEvent glob(int code) {
        return new Widget.GlobKeyEvent(key(code));
    }

    private static KeyEvent key(int code) {
        return new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }

    private static class RecordingWidget extends Widget {
        boolean used;

        @Override public boolean globtype(GlobKeyEvent ev) {
            if(Fightsess.isCombatActionKey(ev)) {
                used = true;
                return true;
            }
            return false;
        }
    }

    private static class RecordingBelt extends Widget {
        Fightsess session;
        boolean used;

        @Override public boolean globtype(GlobKeyEvent ev) {
            if(Fightsess.capturesBeltKey(session, ev))
                return false;
            used = true;
            return true;
        }
    }
}
