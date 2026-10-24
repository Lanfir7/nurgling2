package nurgling.widgets;

import haven.Coord;
import haven.GameUI;
import haven.ICheckBox;
import haven.KeyBinding;
import haven.KeyMatch;
import haven.Tex;
import haven.TexI;
import haven.Widget;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NMiniMapTogglePluginTest {
    private NConfig previousConfig;

    @BeforeEach
    void configure() {
        previousConfig = NConfig.current;
        NConfig.current = new NConfig();
    }

    @AfterEach
    void restore() {
        NConfig.current = previousConfig;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class ProbeMap extends NMiniMapWnd.Map {
        int clicks;
        int moves;
        private ProbeMap() { super(Coord.z, null, null); }
        @Override public boolean mousedown(Widget.MouseDownEvent ev) { clicks++; return true; }
        @Override public void mousemove(Widget.MouseMoveEvent ev) { moves++; }
        @Override public boolean tooltip(Widget.TooltipQuery ev) { return ev.set("map", this); }
    }

    private static final class ProbeStatus extends Widget {
        int moves;
        @Override public void mousemove(Widget.MouseMoveEvent ev) { moves++; }
    }

    private static final class DecliningButton extends ICheckBox {
        int attempts;
        DecliningButton(Tex tex) { super(tex, tex); }
        @Override public boolean mousedown(Widget.MouseDownEvent ev) {
            attempts++;
            return false;
        }
    }

    private static final class Fixture {
        final NMiniMapWnd wnd;
        final ProbeMap map;
        final ProbeStatus status;
        final ICheckBox builtin;
        final NIconDock dock;
        final NUI ui;

        Fixture() throws Exception {
            this(allocate(NUI.class));
        }

        Fixture(NUI ui) throws Exception {
            this.ui = ui;
            wnd = allocate(NMiniMapWnd.class);
            wnd.ui = ui;
            wnd.sz = new Coord(100, 100);
            wnd.c = Coord.z;
            map = allocate(ProbeMap.class);
            map.sz = wnd.sz;
            map.c = Coord.z;
            map.visible = true;
            wnd.miniMap = map;
            wnd.add(map);
            status = wnd.add(new ProbeStatus(), Coord.z);
            status.resize(new Coord(5, 5));
            wnd.toggle_panel = wnd.add(new Widget());
            builtin = button(20);
            wnd.toggle_panel.add(builtin, Coord.z);
            wnd.toggle_panel.pack();
            wnd.toggle_panel.move(new Coord(0, 80));
            wnd.map_box = wnd.add(button(20), new Coord(80, 0));
            dock = new NIconDock();
            Field field = NMiniMapWnd.class.getDeclaredField("dock");
            field.setAccessible(true);
            field.set(wnd, dock);
        }
    }

    private static NUI ui(String character) throws Exception {
        NUI ui = allocate(NUI.class);
        NGameUI gui = allocate(NGameUI.class);
        Field chrid = GameUI.class.getField("chrid");
        chrid.setAccessible(true);
        chrid.set(gui, character);
        gui.ui = ui;
        ui.gui = gui;
        return ui;
    }

    private static List<Widget> children(Widget parent) {
        return new ArrayList<>(parent.children());
    }

    private static ICheckBox button(int size) {
        TexI tex = icon(size);
        ICheckBox button = new ICheckBox(tex, tex);
        button.settip("plugin");
        return button;
    }

    @Test
    void hiddenDockDoesNotClickOrTooltipAnUndrawnPluginButton() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger pluginClicks = new AtomicInteger();
        ICheckBox plugin = button(20);
        plugin.click(pluginClicks::incrementAndGet);
        f.wnd.addToggle(plugin);
        assertTrue(f.dock.hidden());
        Coord centre = f.wnd.toggle_panel.c.add(plugin.c).add(plugin.sz.div(2));
        assertTrue(new Widget.MouseDownEvent(centre, 1).dispatch(f.wnd));
        assertEquals(0, pluginClicks.get());
        assertEquals(1, f.map.clicks);
        Widget.TooltipQuery tip = new Widget.TooltipQuery(centre, null);
        assertTrue(tip.dispatch(f.wnd));
        assertEquals("map", tip.ret);
        assertSame(f.map, tip.from);
    }

    @Test
    void invalidAndForeignButtonsAreRejectedWithoutChangingEitherWindow() throws Exception {
        Fixture a = new Fixture(ui("A")), b = new Fixture(ui("B"));
        List<Widget> beforeA = children(a.wnd.toggle_panel);
        List<Widget> beforeB = children(b.wnd.toggle_panel);
        assertThrows(IllegalArgumentException.class, () -> a.wnd.addToggle(null));
        assertThrows(IllegalArgumentException.class, () -> a.wnd.addToggle(new Widget(new Coord(20, 20))));

        ICheckBox owned = button(20);
        Widget otherParent = new Widget();
        otherParent.add(owned);
        assertThrows(IllegalArgumentException.class, () -> a.wnd.addToggle(owned));
        assertSame(otherParent, owned.parent);

        ICheckBox wrongUi = button(20);
        wrongUi.ui = b.ui;
        assertThrows(IllegalArgumentException.class, () -> a.wnd.addToggle(wrongUi));
        assertNull(wrongUi.parent);

        ICheckBox zero = button(20);
        zero.resize(Coord.z);
        assertThrows(IllegalArgumentException.class, () -> a.wnd.addToggle(zero));
        ICheckBox oversized = button(120);
        assertThrows(IllegalArgumentException.class, () -> a.wnd.addToggle(oversized));
        assertEquals(beforeA, children(a.wnd.toggle_panel));
        assertEquals(beforeB, children(b.wnd.toggle_panel));
    }

    @Test
    void addIsIdempotentAndReflowPreservesButtonsBindingsAndOrder() throws Exception {
        Fixture f = new Fixture();
        List<Widget> originals = children(f.wnd.toggle_panel);
        AtomicInteger clicks = new AtomicInteger();
        ICheckBox plugin = button(20);
        plugin.click(clicks::incrementAndGet);
        KeyBinding binding = KeyBinding.get("test/plugin-toggle", KeyMatch.nil);
        plugin.setgkey(binding);
        f.wnd.addToggle(plugin);
        f.wnd.addToggle(plugin);
        assertEquals(originals.size() + 1, children(f.wnd.toggle_panel).size());
        assertSame(plugin, children(f.wnd.toggle_panel).get(originals.size()));
        assertSame(f.wnd.toggle_panel, plugin.parent);
        assertSame(f.ui, plugin.ui);

        List<Widget> ordered = children(f.wnd.toggle_panel);
        for(int i = 0; i < 5; i++) f.wnd.addToggle(button(20));
        List<Widget> wrapped = children(f.wnd.toggle_panel);
        assertEquals(new Coord(0, 23), wrapped.get(4).c);
        assertTrue(f.wnd.toggle_panel.sz.y > 20);
        assertEquals(new Coord(0, f.map.sz.y - f.wnd.toggle_panel.sz.y), f.wnd.toggle_panel.c);

        f.wnd.resize(new Coord(75, 100));
        List<Widget> resized = children(f.wnd.toggle_panel);
        assertEquals(wrapped, resized);
        assertEquals(new Coord(0, 23), resized.get(3).c);
        f.wnd.resize(new Coord(100, 100));
        assertEquals(wrapped, children(f.wnd.toggle_panel));
        assertEquals(ordered.subList(0, 2), children(f.wnd.toggle_panel).subList(0, 2));
        assertSame(binding, plugin.kb_gkey);
        assertSame(f.wnd.toggle_panel, plugin.parent);
        assertSame(f.ui, plugin.ui);
        plugin.click();
        assertEquals(1, clicks.get());
    }

    @Test
    void revealedScaledHitAndTooltipUseThePluginButtonOnce() throws Exception {
        Fixture f = new Fixture();
        AtomicInteger clicks = new AtomicInteger();
        ICheckBox plugin = button(20);
        plugin.click(clicks::incrementAndGet);
        f.wnd.addToggle(plugin);
        Coord origin = f.wnd.toggle_panel.c.add(plugin.c);
        Coord centre = origin.add(plugin.sz.div(2));
        f.dock.track(centre, f.wnd.sz, 1);
        assertFalse(f.dock.hidden());
        Coord grown = origin.add(plugin.sz.x + 5, plugin.sz.y / 2);
        assertFalse(grown.isect(origin, plugin.sz));
        Method hitMethod = NMiniMapWnd.class.getDeclaredMethod("dockHit", Coord.class);
        hitMethod.setAccessible(true);
        assertSame(plugin, hitMethod.invoke(f.wnd, grown));
        new Widget.MouseMoveEvent(grown).dispatch(f.wnd);
        assertTrue(plugin.h);
        assertEquals(1, f.map.moves);
        assertEquals(1, f.status.moves);
        new Widget.MouseMoveEvent(new Coord(50, 50)).dispatch(f.wnd);
        assertFalse(plugin.h);
        assertEquals(2, f.map.moves);
        assertEquals(2, f.status.moves);
        assertTrue(new Widget.MouseDownEvent(grown, 1).dispatch(f.wnd));
        assertEquals(1, clicks.get());
        assertEquals(0, f.map.clicks);
        Widget.TooltipQuery tip = new Widget.TooltipQuery(grown, null);
        assertTrue(tip.dispatch(f.wnd));
        assertSame(plugin, tip.from);
        Coord empty = new Coord(50, 50);
        assertTrue(new Widget.MouseDownEvent(empty, 1).dispatch(f.wnd));
        assertEquals(1, clicks.get());
        assertEquals(1, f.map.clicks);
    }

    @Test
    void separateCharacterWindowsKeepCallbacksAndTooltipsOnTheirOwnUi() throws Exception {
        NUI uiA = ui("A"), uiB = ui("B"), previous = ThreadLocalUI.get();
        Fixture a = new Fixture(uiA), b = new Fixture(uiB);
        AtomicInteger clicksA = new AtomicInteger(), clicksB = new AtomicInteger();
        ICheckBox buttonA = button(20), buttonB = button(20);
        buttonA.click(clicksA::incrementAndGet);
        buttonB.click(clicksB::incrementAndGet);
        a.wnd.addToggle(buttonA);
        b.wnd.addToggle(buttonB);
        assertThrows(IllegalArgumentException.class, () -> b.wnd.addToggle(buttonA));
        try {
            Coord ca = a.wnd.toggle_panel.c.add(buttonA.c).add(buttonA.sz.div(2));
            Coord cb = b.wnd.toggle_panel.c.add(buttonB.c).add(buttonB.sz.div(2));
            a.dock.track(ca, a.wnd.sz, 1);
            b.dock.track(cb, b.wnd.sz, 1);
            ThreadLocalUI.set(uiB);
            new Widget.MouseDownEvent(ca, 1).dispatch(a.wnd);
            ThreadLocalUI.set(uiA);
            new Widget.MouseDownEvent(cb, 1).dispatch(b.wnd);
            assertEquals(1, clicksA.get());
            assertEquals(1, clicksB.get());
            assertEquals("A", a.wnd.ui.gui.chrid);
            assertEquals("B", b.wnd.ui.gui.chrid);
            assertSame(uiA, buttonA.ui);
            assertSame(uiB, buttonB.ui);
        } finally {
            if(previous == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(previous);
        }
    }

    @Test
    void declinedIconClickFallsThroughToMapExactlyOnce() throws Exception {
        Fixture f = new Fixture();
        DecliningButton button = new DecliningButton(icon(20));
        f.wnd.addToggle(button);
        Coord centre = f.wnd.toggle_panel.c.add(button.c).add(button.sz.div(2));
        f.dock.track(centre, f.wnd.sz, 1);
        assertTrue(new Widget.MouseDownEvent(centre, 1).dispatch(f.wnd));
        assertEquals(1, button.attempts);
        assertEquals(1, f.map.clicks);
    }

    @Test
    void ownTooltipIsLimitedToWindowBoundsAndBuiltinsKeepTheirTip() throws Exception {
        Fixture f = new Fixture();
        f.wnd.tooltip = "window";
        Widget.TooltipQuery outside = new Widget.TooltipQuery(new Coord(130, 130), null);
        assertFalse(outside.dispatch(f.wnd));
        assertNull(outside.ret);

        f.builtin.settip("builtin");
        Coord centre = f.wnd.toggle_panel.c.add(f.builtin.c).add(f.builtin.sz.div(2));
        f.dock.track(centre, f.wnd.sz, 1);
        Widget.TooltipQuery tip = new Widget.TooltipQuery(centre, null);
        assertTrue(tip.dispatch(f.wnd));
        assertSame(f.builtin, tip.from);
    }

    private static TexI icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for(int x = 0; x < size; x++)
            for(int y = 0; y < size; y++)
                image.setRGB(x, y, 0xffffffff);
        return new TexI(image);
    }
}
