package haven;

import nurgling.*;
import nurgling.hotkeys.*;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.*;
import sun.misc.Unsafe;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FinalGameplayHandlersTest {
    static {
        try {
            java.net.URLClassLoader resources = new java.net.URLClassLoader(new java.net.URL[]{
                    java.nio.file.Paths.get("bin", "builtin-res.jar").toUri().toURL(),
                    java.nio.file.Paths.get("bin", "hafen-res.jar").toUri().toURL()});
            Resource.local().add(new Resource.FileSource(java.nio.file.Paths.get("resources", "compiled", "res")));
            Resource.local().add(name -> {
                java.io.InputStream stream = resources.getResourceAsStream("res/" + name + ".res");
                if(stream == null) throw new java.io.FileNotFoundException(name);
                return stream;
            });
        } catch(Exception e) { throw new AssertionError(e); }
    }
    private NUI ui;
    private NUI previous;
    private TestMap map;
    private final Map<String, InputGesture> bindings = new HashMap<>();
    private final Map<String, String> preferences = new HashMap<>();

    @BeforeEach void setup() throws Exception {
        previous = ThreadLocalUI.get();
        ui = allocate(NUI.class);
        ui.sessionConfig = new NConfig();
        ui.core = allocate(NCore.class);
        ui.core.mode = NCore.Mode.IDLE;
        field(UI.class, "grabs").set(ui, new java.util.concurrent.CopyOnWriteArrayList<>());
        field(UI.class, "widgets").set(ui, new HashMap<>());
        field(UI.class, "rwidgets").set(ui, new HashMap<>());
        ThreadLocalUI.set(ui);
        map = allocate(TestMap.class);
        map.ui = ui;
        map.parent = new Widget();
        map.parent.setfocusctl(true);
        map.parent.ui = ui;
        map.c = Coord.z;
        map.sz = new Coord(100, 100);
        map.messages = new ArrayList<>();
        map.camera = map.new TestCamera();
    }

    @AfterEach void restore() {
        for(Map.Entry<String, InputGesture> entry : bindings.entrySet()) {
            Hotkeys.action(entry.getKey()).binding().set(entry.getValue());
            Utils.setpref(preferenceKey(entry.getKey()), preferences.get(entry.getKey()));
        }
        if(previous == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(previous);
    }

    private String preferenceKey(String id) {
        return (Hotkeys.action(id).binding() instanceof KeyBindingHotkey ? "keybind/" : "gesturebind/") + id;
    }

    private void bind(String id, InputGesture gesture) {
        if(!bindings.containsKey(id)) {
            bindings.put(id, Hotkeys.action(id).current());
            preferences.put(id, Utils.getpref(preferenceKey(id), null));
        }
        Hotkeys.action(id).binding().set(gesture);
    }

    private void mods(int mods) throws Exception {
        ui.modctrl = (mods & KeyMatch.C) != 0;
        ui.modshift = (mods & KeyMatch.S) != 0;
        ui.modmeta = (mods & KeyMatch.M) != 0;
        field(NUI.class, "cachedModFlags").set(ui, -1);
    }

    @Test void reboundWorldPingWinsBeforeMiddleButtonCameraAndKeepsServerLeftButton() throws Exception {
        bind(Hotkeys.WORLD_PING, InputGesture.mouse(2, 7, KeyMatch.C));
        mods(KeyMatch.C);
        assertTrue(map.mousedown(new Widget.MouseDownEvent(Coord.z, 2)));
        assertEquals(0, map.cameraClicks);
        assertEquals(1, map.messages.size());
        assertArrayEquals(new Object[]{"click", Coord.z, Coord2d.of(22, 33).floor(OCache.posres), 1, 5}, map.messages.get(0));
    }

    @Test void consumingModalGrabberPrecedesConfiguredWorldClick() throws Exception {
        bind(Hotkeys.WORLD_PING, InputGesture.mouse(2, 7, KeyMatch.C)); mods(KeyMatch.C);
        final int[] presses = {0};
        map.grab(new MapView.Grabber() {
            public boolean mmousedown(Coord c, int button) { presses[0]++; return true; }
            public boolean mmouseup(Coord c, int button) { return false; }
            public boolean mmousewheel(Coord c, int amount) { return false; }
            public void mmousemove(Coord c) { }
        });
        assertTrue(map.mousedown(new Widget.MouseDownEvent(Coord.z, 2)));
        assertEquals(1, presses[0]);
        assertTrue(map.messages.isEmpty());
        assertEquals(0, map.cameraClicks);
        TestNMap nmap = allocate(TestNMap.class);
        nmap.ui = ui; nmap.parent = map.parent; nmap.camera = map.camera; nmap.messages = new ArrayList<>();
        nmap.grab(new MapView.Grabber() {
            public boolean mmousedown(Coord c, int button) { presses[0]++; return true; }
            public boolean mmouseup(Coord c, int button) { return false; }
            public boolean mmousewheel(Coord c, int amount) { return false; }
            public void mmousemove(Coord c) { }
        });
        assertTrue(nmap.mousedown(new Widget.MouseDownEvent(Coord.z, 2)));
        assertEquals(2, presses[0]);
        assertTrue(nmap.messages.isEmpty());
        assertEquals(0, map.cameraClicks);
    }

    @Test void placementConsumesWorldShortcutBeforeOrdinaryClickRouting() throws Exception {
        bind(Hotkeys.WORLD_PING, InputGesture.mouse(1, 7, KeyMatch.C)); mods(KeyMatch.C);
        TestMap.TestPlob plob = allocate(TestMap.TestPlob.class);
        field(MapView.Plob.class, "this$0").set(plob, map);
        plob.rc = Coord2d.of(22, 33); plob.lastmc = Coord.z;
        Loader.Future<MapView.Plob> future = allocate(Loader.Future.class);
        field(Loader.Future.class, "val").set(future, plob);
        field(Loader.Future.class, "done").set(future, true); map.placing = future;
        assertTrue(map.mousedown(new Widget.MouseDownEvent(Coord.z, 1)));
        assertEquals(1, map.messages.size());
        assertEquals("place", map.messages.get(0)[0]);
        assertEquals(0, map.cameraClicks);
    }

    @Test void quickChatConsumesDefaultAndReboundGlobalKeyButNotDisabledBinding() throws Exception {
        for(InputGesture gesture : new InputGesture[]{Hotkeys.action("chat-quick").defaultGesture(),
                InputGesture.key(KeyMatch.forcode(KeyEvent.VK_CLOSE_BRACKET, KeyMatch.M)), InputGesture.none()}) {
            bind("chat-quick", gesture);
            ChatUI chat = allocate(ChatUI.class);
            chat.ui = ui; chat.sel = allocate(ChatUI.SimpleChat.class); chat.visible = false;
            Widget.GlobKeyEvent event = new Widget.GlobKeyEvent(key(
                    gesture.type() == InputGesture.Type.NONE ? KeyEvent.VK_ENTER : gesture.key().code,
                    gesture.type() == InputGesture.Type.NONE ? 0 : gesture.modmatch()).awt);
            assertEquals(gesture.type() != InputGesture.Type.NONE, chat.globtype(event));
            assertEquals(gesture.type() != InputGesture.Type.NONE, field(ChatUI.class, "qgrab").get(chat) != null);
        }
    }

    @Test void heldControlLeftDropReachesWorldWithCanonicalControlAfterRebinding() throws Exception {
        String id = "held.drop_on_ground";
        assertNotNull(Hotkeys.registry().find(id));
        bind(id, Hotkeys.action(id).defaultGesture());
        ItemDrag drag = allocate(ItemDrag.class);
        drag.ui = ui; drag.parent = map; drag.c = Coord.z; drag.doff = Coord.z;
        mods(KeyMatch.C);
        assertTrue(drag.mousedown((Widget.MouseDownEvent)new Widget.MouseDownEvent(Coord.z, 1).grabbed(true)));
        assertEquals("drop", map.messages.get(0)[0]);
        assertEquals(2, map.messages.get(0)[3]);
        map.messages.clear();
        bind(id, InputGesture.mouse(2, 7, KeyMatch.S));
        mods(KeyMatch.S);
        assertTrue(drag.mousedown((Widget.MouseDownEvent)new Widget.MouseDownEvent(Coord.z, 2).grabbed(true)));
        assertEquals(2, map.messages.get(0)[3]);
        bind(id, InputGesture.none());
        map.messages.clear();
        assertFalse(drag.mousedown((Widget.MouseDownEvent)new Widget.MouseDownEvent(Coord.z, 2).grabbed(true)));
        assertTrue(map.messages.isEmpty());
    }

    @Test void nurglingWorldActionsKeepTheirLogicalButtonOnEveryPhysicalButton() throws Exception {
        // No terrain actions in this isolated scene; exercise the real SERVER fallback.
        List<?> terrain = (List<?>)field(nurgling.contextmenu.TileContextRegistry.class, "actions").get(null);
        List savedTerrain = new ArrayList(terrain);
        terrain.clear();
        try {
        TestNMap nmap = allocate(TestNMap.class);
        nmap.ui = ui; nmap.parent = map.parent; nmap.c = Coord.z; nmap.sz = map.sz;
        nmap.camera = map.camera; nmap.messages = new ArrayList<>();
        field(NMapView.class, "isAreaSelectionMode").set(nmap, new java.util.concurrent.atomic.AtomicBoolean());
        field(NMapView.class, "isGobSelectionMode").set(nmap, new java.util.concurrent.atomic.AtomicBoolean());
        nurgling.overlays.NWaypointOverlay overlay = allocate(nurgling.overlays.NWaypointOverlay.class);
        nurgling.overlays.NWaypointOverlay.WNode node = allocate(nurgling.overlays.NWaypointOverlay.WNode.class);
        field(node.getClass(), "id").set(node, 1L); node.sc = Coord.z;
        field(overlay.getClass(), "screen").set(overlay, Collections.singletonList(node));
        field(NMapView.class, "wpOverlay").set(nmap, overlay);
        for(String id : new String[]{Hotkeys.WORLD_CONTEXT_MENU, Hotkeys.WORLD_PING, Hotkeys.WORLD_QUEUE_WAYPOINT}) {
            for(int button = 1; button <= 3; button++) {
              for(int physicalMods : new int[]{0, 7}) {
                bind(id, InputGesture.mouse(button, 7, physicalMods));
                mods(physicalMods); nmap.messages.clear();
                assertTrue(nmap.mousedown(new Widget.MouseDownEvent(Coord.z, button)), id);
                assertEquals(0, map.cameraClicks, id);
                assertEquals(1, nmap.messages.size(), id);
                assertEquals(id.equals(Hotkeys.WORLD_CONTEXT_MENU) ? 3 : 1, nmap.messages.get(0)[3], id);
                assertEquals(id.equals(Hotkeys.WORLD_CONTEXT_MENU) ? 2 : id.equals(Hotkeys.WORLD_PING) ? 5 : 4,
                        nmap.messages.get(0)[4], id);
              }
            }
            bind(id, InputGesture.none());
        }
        } finally { ((List)terrain).addAll(savedTerrain); }
    }

    @Test void heldControlOpenCanBeDisabledWithoutLegacyFallback() throws Exception {
        NGameUI gui = allocate(NGameUI.class); gui.ui = ui; gui.map = map;
        ui.root = allocate(RootWidget.class); ui.root.ui = ui;
        gui.parent = ui.root; gui.c = Coord.z;
        map.parent = gui;
        ItemDrag drag = allocate(ItemDrag.class);
        drag.ui = ui; drag.parent = map; drag.c = Coord.z; drag.doff = Coord.z;
        bind(Hotkeys.HELD_OPEN_WITH_CONTROL, InputGesture.mouse(2, 7, KeyMatch.S));
        mods(KeyMatch.S);
        assertTrue(drag.mousedown((Widget.MouseDownEvent)new Widget.MouseDownEvent(Coord.z, 2).grabbed(true)));
        assertEquals(3, map.messages.get(0)[3]); assertEquals(0, map.messages.get(0)[4]);
        bind(Hotkeys.HELD_OPEN_WITH_CONTROL, InputGesture.none());
        map.messages.clear(); mods(KeyMatch.C);
        assertFalse(drag.mousedown((Widget.MouseDownEvent)new Widget.MouseDownEvent(Coord.z, 3).grabbed(true)));
        assertTrue(map.messages.isEmpty());
    }

    @Test void stockpileWheelPreservesModifiersAndRejectsDisabledGesture() throws Exception {
        TestISBox box = allocate(TestISBox.class);
        box.ui = ui;
        box.messages = new ArrayList<>();
        bind(Hotkeys.STOCKPILE_TRANSFER_OUT, InputGesture.wheel(-1, KeyMatch.MODS, 0));
        mods(KeyMatch.S);

        assertTrue(box.mousewheel(new Widget.MouseWheelEvent(Coord.z, -1, -1)));
        assertArrayEquals(new Object[]{"xfer2", -1, KeyMatch.S}, box.messages.get(0));

        bind(Hotkeys.STOCKPILE_TRANSFER_OUT, InputGesture.none());
        bind(Hotkeys.STOCKPILE_TRANSFER_OUT_ALL, InputGesture.none());
        box.messages.clear();
        assertFalse(box.mousewheel(new Widget.MouseWheelEvent(Coord.z, -1, -1)));
        assertTrue(box.messages.isEmpty());
    }

    @Test void controlRightClickOnInventoryItemOpensFlowerMenuForAllMatchingItems() throws Exception {
        TestGItem item = new TestGItem();
        WItem widget = new WItem(item);
        widget.ui = ui;
        widget.parent = allocate(NInventory.class);
        widget.parent.ui = ui;
        mods(KeyMatch.C);

        assertTrue(widget.mousedown(new Widget.MouseDownEvent(Coord.z, 3)));
        assertArrayEquals(new Object[]{"iact", Coord.z, UI.MOD_CTRL}, item.messages.get(0));
        assertSame(widget, ui.core.getLastActions().item);
    }

    @Test void placementKeyboardCoarseAndFineUseLogicalRotationParameters() throws Exception {
        TestMap.TestPlob plob = allocate(TestMap.TestPlob.class);
        field(MapView.Plob.class, "this$0").set(plob, map);
        plob.adjust = new MapView.StdPlace(); plob.a = 0; plob.rc = Coord2d.z;
        Loader.Future<MapView.Plob> future = allocate(Loader.Future.class);
        field(Loader.Future.class, "val").set(future, plob);
        field(Loader.Future.class, "done").set(future, true);
        map.placing = future;
        bind(Hotkeys.WORLD_PLACEMENT_ROTATE_LEFT, Hotkeys.action(Hotkeys.WORLD_PLACEMENT_ROTATE_LEFT).defaultGesture());
        assertTrue(map.keydown(key(KeyEvent.VK_LEFT, 0)));
        assertEquals(-Math.PI / 4, plob.a, 1e-9);
        bind(Hotkeys.WORLD_PLACEMENT_ROTATE_LEFT, InputGesture.key(KeyMatch.forcode(KeyEvent.VK_J, KeyMatch.M)));
        mods(KeyMatch.M);
        assertTrue(map.keydown(key(KeyEvent.VK_J, KeyMatch.M)));
        assertEquals(-Math.PI / 2, plob.a, 1e-9);
        bind(Hotkeys.WORLD_PLACEMENT_ROTATE_LEFT, InputGesture.none());
        map.keydown(key(KeyEvent.VK_J, KeyMatch.M));
        assertEquals(-Math.PI / 2, plob.a, 1e-9);
    }

    @Test void placementFineWheelAndPositionModesAreRebindableAndDisableable() throws Exception {
        TestMap.TestPlob plob = allocate(TestMap.TestPlob.class);
        field(MapView.Plob.class, "this$0").set(plob, map);
        plob.adjust = new MapView.StdPlace(); plob.a = 0; plob.rc = Coord2d.z;
        Loader.Future<MapView.Plob> future = allocate(Loader.Future.class);
        field(Loader.Future.class, "val").set(future, plob);
        field(Loader.Future.class, "done").set(future, true); map.placing = future;
        bind("world.placement.fine_wheel_left", InputGesture.wheel(1, 7, KeyMatch.M));
        mods(KeyMatch.M);
        assertTrue(map.mousewheel(new Widget.MouseWheelEvent(Coord.z, 1, 1)));
        assertEquals(-Math.PI / MapView.plobagran, plob.a, 1e-9);
        bind("world.placement.fine_wheel_left", InputGesture.none());
        map.mousewheel(new Widget.MouseWheelEvent(Coord.z, 1, 1));
        assertEquals(-Math.PI / MapView.plobagran, plob.a, 1e-9);
        bind("world.placement.snap_neighbors", InputGesture.none());
        bind("world.placement.free_position", InputGesture.modifier(KeyMatch.C));
        Coord2d point = Coord2d.of(2.1, 3.2);
        plob.adjust.adjust(plob, Coord.z, point, KeyMatch.C);
        assertNotEquals(Coord2d.of(5.5, 5.5), plob.rc);
        bind("world.placement.free_position", InputGesture.none());
        plob.adjust.adjust(plob, Coord.z, point, KeyMatch.C);
        assertEquals(Coord2d.of(5.5, 5.5), plob.rc);
        assertNotNull(Hotkeys.registry().find("world.placement.snap_edges"));
    }

    @Test void placementDefaultsPreserveControlShiftAndAltCombinations() throws Exception {
        TestMap.TestPlob plob = allocate(TestMap.TestPlob.class);
        field(MapView.Plob.class, "this$0").set(plob, map);
        plob.adjust = new MapView.StdPlace(); plob.rc = Coord2d.z;
        Loader.Future<MapView.Plob> future = allocate(Loader.Future.class);
        field(Loader.Future.class, "val").set(future, plob);
        field(Loader.Future.class, "done").set(future, true); map.placing = future;
        for(String id : Hotkeys.PLACEMENT_KEYS) bind(id, Hotkeys.action(id).defaultGesture());
        for(String id : Hotkeys.PLACEMENT_WHEELS) bind(id, Hotkeys.action(id).defaultGesture());
        for(int modifiers : new int[]{KeyMatch.C, KeyMatch.C | KeyMatch.M, KeyMatch.S,
                KeyMatch.S | KeyMatch.C, KeyMatch.S | KeyMatch.M, KeyMatch.MODS}) {
            double step = (modifiers & KeyMatch.S) != 0 ? Math.PI / MapView.plobagran : Math.PI / 4;
            for(int direction : new int[]{-1, 1}) {
                mods(modifiers); plob.a = 0;
                assertTrue(map.keydown(key(direction < 0 ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT, modifiers)));
                assertEquals(direction * step, plob.a, 1e-9, "key modifiers " + modifiers);
                plob.a = 0;
                assertTrue(map.mousewheel(new Widget.MouseWheelEvent(Coord.z, direction, direction)));
                assertEquals(direction * step, plob.a, 1e-9, "wheel modifiers " + modifiers);
            }
        }
        for(String id : Hotkeys.PLACEMENT_KEYS) bind(id, InputGesture.none());
        for(String id : Hotkeys.PLACEMENT_WHEELS) bind(id, InputGesture.none());
        plob.a = 0;
        for(int modifiers : new int[]{0, KeyMatch.C, KeyMatch.S, KeyMatch.MODS}) {
            mods(modifiers);
            map.keydown(key(KeyEvent.VK_LEFT, modifiers));
            map.mousewheel(new Widget.MouseWheelEvent(Coord.z, -1, -1));
            assertEquals(0, plob.a, 1e-9, "disabled modifiers " + modifiers);
        }
    }

    @Test void eachPlacementPositionModeCanBeReboundAndDisabledThroughTheRealAdjuster() throws Exception {
        TestMap.TestPlob plob = allocate(TestMap.TestPlob.class);
        field(MapView.Plob.class, "this$0").set(plob, map);
        field(Gob.class, "attr").set(plob, new HashMap<>());
        plob.rc = Coord2d.z;
        String[] ids = {"world.placement.snap_neighbors", "world.placement.free_position", "world.placement.snap_edges"};
        Coord2d point = Coord2d.of(2.1, 3.2), tileCenter = Coord2d.of(5.5, 5.5);
        for(String id : ids) bind(id, InputGesture.none());
        for(String id : ids) {
            plob.adjust = id.endsWith("snap_edges") ? new NStdPlace() : new MapView.StdPlace();
            InputGesture defaults = Hotkeys.action(id).defaultGesture();
            bind(id, defaults);
            plob.adjust.adjust(plob, Coord.z, point, defaults.code());
            assertNotEquals(tileCenter, plob.rc, id + " default");
            int rebound = defaults.code() == KeyMatch.M ? KeyMatch.C : KeyMatch.M;
            bind(id, InputGesture.modifier(rebound));
            plob.adjust.adjust(plob, Coord.z, point, defaults.code());
            assertEquals(tileCenter, plob.rc, id + " old modifier");
            plob.adjust.adjust(plob, Coord.z, point, rebound);
            assertNotEquals(tileCenter, plob.rc, id + " rebound");
            bind(id, InputGesture.none());
            plob.adjust.adjust(plob, Coord.z, point, KeyMatch.MODS);
            assertEquals(tileCenter, plob.rc, id + " disabled with all modifiers held");
        }
    }

    private static Widget.KeyDownEvent key(int code, int mods) {
        return new Widget.KeyDownEvent(new KeyEvent(new java.awt.Canvas(), KeyEvent.KEY_PRESSED, 0,
                ((mods & KeyMatch.M) != 0 ? KeyEvent.ALT_DOWN_MASK : 0) |
                ((mods & KeyMatch.C) != 0 ? KeyEvent.CTRL_DOWN_MASK : 0) |
                ((mods & KeyMatch.S) != 0 ? KeyEvent.SHIFT_DOWN_MASK : 0), code, KeyEvent.CHAR_UNDEFINED));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(((Unsafe)field(Unsafe.class, "theUnsafe").get(null)).allocateInstance(type));
    }

    private static class TestMap extends MapView {
        List<Object[]> messages;
        int cameraClicks;
        TestMap() { super(Coord.z, null, Coord2d.z, 0); }
        // Substitute only GPU picking; the actual hit callback and server message stay real.
        protected void runHitTest(Hittest test) { test.hit(Coord.z, Coord2d.of(22, 33), null); }
        public void wdgmsg(String name, Object... args) {
            Object[] message = new Object[args.length + 1]; message[0] = name;
            System.arraycopy(args, 0, message, 1, args.length); messages.add(message);
        }
        public Gob player() { return null; }
        class TestCamera extends Camera {
            public float angle() { return 0; }
            public void tick(double dt) { }
            public boolean click(Coord c) { cameraClicks++; return true; }
            public boolean keydown(Widget.KeyDownEvent ev) { return false; }
        }
        class TestPlob extends Plob {
            TestPlob() { super(null, Message.nil); }
            public void move(Coord2d c, double angle) { rc = c; a = angle; }
        }
    }

    private static class TestNMap extends NMapView {
        List<Object[]> messages;
        TestNMap() { super(Coord.z, null, Coord2d.z, 0); }
        protected void runHitTest(Hittest test) { test.hit(Coord.z, Coord2d.of(22, 33), null); }
        public boolean sendPointPing(Coord2d c) { return false; }
        public boolean addWaypointAt(Coord2d c) { return false; }
        public void wdgmsg(String name, Object... args) {
            Object[] message = new Object[args.length + 1]; message[0] = name;
            System.arraycopy(args, 0, message, 1, args.length); messages.add(message);
        }
    }

    private static class TestISBox extends ISBox {
        List<Object[]> messages;
        TestISBox() { super(null, 0, 0, -1); }
        public String stockpileItemName() { return null; }
        public void wdgmsg(String name, Object... args) {
            Object[] message = new Object[args.length + 1]; message[0] = name;
            System.arraycopy(args, 0, message, 1, args.length); messages.add(message);
        }
    }

    private static class TestGItem extends GItem {
        final List<Object[]> messages = new ArrayList<>();
        TestGItem() { super(null); }
        protected void updateraw() { }
        public void wdgmsg(String name, Object... args) {
            Object[] message = new Object[args.length + 1]; message[0] = name;
            System.arraycopy(args, 0, message, 1, args.length); messages.add(message);
        }
    }
}
