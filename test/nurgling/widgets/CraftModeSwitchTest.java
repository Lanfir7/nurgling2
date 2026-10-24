package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import nurgling.ClientResourceFixture;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import sun.misc.Unsafe;
import static org.junit.jupiter.api.Assertions.*;

class CraftModeSwitchTest {
    @Test void explicitSelectionIsIdempotentAndRejectsOtherButtonsAndOutsideClicks() {
        CraftModeSwitch control = new CraftModeSwitch();
        AtomicInteger changes = new AtomicInteger();
        control.changed(value -> changes.incrementAndGet());
        try {
            assertTrue(control.mousedown(new Widget.MouseDownEvent(Coord.of(control.sz.x - 1, 1), 1)));
            assertTrue(control.state());
            control.mousedown(new Widget.MouseDownEvent(Coord.of(control.sz.x - 1, 1), 1));
            assertEquals(1, changes.get());
            assertFalse(control.mousedown(new Widget.MouseDownEvent(Coord.of(1, 1), 3)));
            assertFalse(control.mousedown(new Widget.MouseDownEvent(Coord.of(-1, 1), 1)));
            assertFalse(control.mousedown(new Widget.MouseDownEvent(control.sz, 1)));
            assertTrue(control.state());
            control.mousedown(new Widget.MouseDownEvent(Coord.of(1, 1), 1));
            assertFalse(control.state());
            assertEquals(2, changes.get());
        } finally { control.destroy(); }
    }

    private static Widget field(NMakewindow window, String name) throws Exception {
        Field f = NMakewindow.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Widget) f.get(window);
    }

    private static boolean overlaps(Widget a, Widget b) {
        return a.c.x < b.c.x + b.sz.x && b.c.x < a.c.x + a.sz.x
                && a.c.y < b.c.y + b.sz.y && b.c.y < a.c.y + a.sz.y;
    }

    @Test void actualRecipeClicksAndProgrammaticModesKeepChoicesAndControlsInSync() throws Exception {
        NConfig before = NConfig.current;
        NConfig.current = new NConfig();
        try (ClientResourceFixture resources = new ClientResourceFixture()) {
            for (boolean database : new boolean[]{false, true}) {
                for (boolean noTransfer : new boolean[]{false, true}) {
                    NConfig.set(NConfig.Key.ndbenable, database);
                    NConfig.set(NConfig.Key.craftUseStorageIndex, true);
                    NMakewindow window = new NMakewindow("Milk and Butter");
                    Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                    unsafeField.setAccessible(true);
                    window.ui = (UI) ((Unsafe) unsafeField.get(null)).allocateInstance(UI.class);
                    try {
                        CraftModeSwitch control = (CraftModeSwitch) field(window, "autoChk");
                        NMakewindow.Spec milk = window.new Spec(() -> null, MessageBuf.nil, -1, new Object[0]);
                        milk.name = "Goat's Milk";
                        window.inputs = List.of(milk);
                        window.noTransfer.a = noTransfer;
                        assertFalse(window.autoMode);
                        assertTrue(new Widget.MouseDownEvent(control.c.add(control.sz.x - 1, 1), 1).dispatch(window));
                        assertTrue(window.autoMode);
                        assertTrue(control.state());
                        assertEquals(database, field(window, "storageIndexBox").visible);
                        assertTrue(window.noTransfer.visible);
                        assertEquals(noTransfer, window.noTransfer.a);
                        assertSame(milk, window.inputs.get(0));
                        for (Widget child = window.child; child != null; child = child.next)
                            if (child != control && child.visible)
                                assertFalse(overlaps(control, child), "Mode selector overlaps " + child.getClass());
                        window.setAutoMode(false);
                        assertFalse(control.state());
                        assertFalse(window.noTransfer.visible);
                        assertEquals(noTransfer, window.noTransfer.a);
                        assertFalse(field(window, "storageIndexBox").visible);
                        window.enableAutoMode();
                        assertTrue(control.state());
                        assertSame(milk, window.inputs.get(0));
                        assertEquals(Boolean.TRUE, NConfig.get(NConfig.Key.craftUseStorageIndex));
                        // Existing recipe bots also set the public mode field directly.
                        window.autoMode = false;
                        assertFalse(control.state());
                        assertTrue(control.mousedown(new Widget.MouseDownEvent(Coord.of(control.sz.x - 1, 1), 1)));
                        assertTrue(window.autoMode);
                        window.setAutoMode(false);
                        window.autoMode = true;
                        assertTrue(control.state());
                        assertTrue(control.mousedown(new Widget.MouseDownEvent(Coord.of(1, 1), 1)));
                        assertFalse(window.autoMode);
                        assertEquals(noTransfer, window.noTransfer.a);
                    } finally { window.ui = null; window.destroy(); }
                }
            }
        } finally { NConfig.current = before; }
    }
}
