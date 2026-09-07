package nurgling.hotkeys;

import haven.*;
import nurgling.LabeledMarkService;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.routes.ForagerPath;
import nurgling.routes.ForagerWaypoint;
import nurgling.sessions.ThreadLocalUI;
import nurgling.widgets.NMiniMap;
import nurgling.widgets.bots.PathRecordable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Task8GameplayRebindingTest {
    @TempDir Path tempDir;
    private Object oldResources;
    private Object oldRemoteResources;
    private NUI oldThreadUI;
    private NUI ui;
    private LabeledMarkService marks;
    private java.net.URLClassLoader resourceJars;
    private final List<BindingState> bindings = new ArrayList<>();

    @BeforeEach
    void isolateHeadlessSession() throws Exception {
        oldResources = field(Resource.class, "_local").get(null);
        oldRemoteResources = field(Resource.class, "_remote").get(null);
        resourceJars = new java.net.URLClassLoader(new java.net.URL[]{
                Paths.get("bin", "builtin-res.jar").toUri().toURL(),
                Paths.get("bin", "hafen-res.jar").toUri().toURL()});
        field(Resource.class, "_local").set(null, new Resource.Pool(
                new Resource.FileSource(Paths.get("resources", "compiled", "res")),
                name -> {
                    java.io.InputStream stream = resourceJars.getResourceAsStream("res/" + name + ".res");
                    if(stream == null) throw new java.io.FileNotFoundException(name);
                    return stream;
                }));
        oldThreadUI = ThreadLocalUI.get();
        // Skip live-session constructors: handlers and their UI grab implementation remain real.
        ui = allocate(NUI.class);
        ui.sessionConfig = new nurgling.NConfig();
        field(UI.class, "grabs").set(ui, new java.util.concurrent.CopyOnWriteArrayList<>());
        ThreadLocalUI.set(ui);
        for(String id : new String[]{Hotkeys.MAP_MARKER_WAYPOINT, Hotkeys.MAP_MARKER_DELETE,
                Hotkeys.MAP_MARKER_EDIT, Hotkeys.MAP_PING}) {
            BindingState saved = new BindingState(Hotkeys.action(id).binding());
            bindings.add(saved);
            saved.binding.set(InputGesture.none());
        }
    }

    @AfterEach
    void restoreGlobalState() throws Exception {
        try {
            if(marks != null) marks.dispose();
        } finally {
            try {
                for(BindingState binding : bindings) binding.restore();
            } finally {
                if(oldThreadUI == null) ThreadLocalUI.clear();
                else ThreadLocalUI.set(oldThreadUI);
                field(Resource.class, "_local").set(null, oldResources);
                field(Resource.class, "_remote").set(null, oldRemoteResources);
                if(resourceJars != null) resourceJars.close();
            }
        }
    }

    // Catches an outer ev.b == 1 guard, or passing a literal LMB into waypoint matching.
    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void viewFrameDispatchesReboundWaypointButton(int button) throws Exception {
        Hotkeys.action(Hotkeys.MAP_MARKER_WAYPOINT).binding()
                .set(InputGesture.mouse(button, KeyMatch.MODS, KeyMatch.C));
        control(true);
        MapWnd window = allocate(MapWnd.class);
        window.ui = ui;
        Frame frame = viewFrame(window);
        Coord interior = frame.sz.div(2);
        assertFalse(frame.checkhit(interior), "fixture must click map interior, not frame border");

        assertFalse(frame.mousedown(new Widget.MouseDownEvent(interior, 1)));
        assertNull(field(Window.class, "dm").get(window));
        assertTrue(frame.mousedown(new Widget.MouseDownEvent(interior, button)));
        UI.Grab grab = (UI.Grab) field(Window.class, "dm").get(window);
        assertNotNull(grab, "real window drag must acquire a mouse grab");
        assertSame(window, grab.owner);
        assertEquals(new Coord(110, 120), field(Window.class, "doff").get(window));
        grab.remove();
        field(Window.class, "dm").set(window, null);

        control(false);
        assertFalse(frame.mousedown(new Widget.MouseDownEvent(interior, button)));
        control(true);
        Hotkeys.action(Hotkeys.MAP_MARKER_WAYPOINT).binding().set(InputGesture.none());
        assertFalse(frame.mousedown(new Widget.MouseDownEvent(interior, button)));
        assertNull(field(Window.class, "dm").get(window));
    }

    // Catches allowing the recorder/queued-waypoint branch to win before labeled deletion.
    @Test
    void labeledLeftDeleteConsumesPressAndDeletesOnReleaseWhileRecording() throws Exception {
        RecordingWindow recorder = new RecordingWindow();
        NMiniMap map = recordingMap(recorder);
        Hotkeys.action(Hotkeys.MAP_MARKER_DELETE).binding().set(InputGesture.mouse(1, 0, 0));

        assertTrue(map.mousedown(new Widget.MouseDownEvent(new Coord(100, 100), 1)));
        assertEquals(1, marks.getMarksForSegment(42).size(), "delete happens on release");
        assertEquals(0, recorder.recordingChecks, "delete press must precede PathRecordable");
        assertTrue(recorder.waypoints.isEmpty());
        assertTrue(map.mouseup(new Widget.MouseUpEvent(new Coord(100, 100), 1)));
        assertTrue(marks.getMarksForSegment(42).isEmpty(), "real mark service must remove the hit mark");
        assertTrue(recorder.waypoints.isEmpty(), "delete must not also record a waypoint");
        assertEquals(0, recorder.recordingChecks);
    }

    @Test
    void viewFrameBorderKeepsOrdinaryLeftDragWithWaypointDisabled() throws Exception {
        MapWnd window = allocate(MapWnd.class);
        window.ui = ui;
        Frame frame = viewFrame(window);
        assertTrue(frame.checkhit(Coord.z));
        assertFalse(frame.mousedown(new Widget.MouseDownEvent(Coord.z, 3)));
        assertNull(field(Window.class, "dm").get(window));
        assertTrue(frame.mousedown(new Widget.MouseDownEvent(Coord.z, 1)));
        UI.Grab grab = (UI.Grab) field(Window.class, "dm").get(window);
        assertNotNull(grab);
        assertSame(window, grab.owner);
        grab.remove();
    }

    // Catches unconditional LMB-delete handling or recording suppression when delete is disabled.
    @Test
    void disabledDeleteLeavesLabeledMarkAndRecordsTheClickedLocation() throws Exception {
        RecordingWindow recorder = new RecordingWindow();
        NMiniMap map = recordingMap(recorder);
        Hotkeys.action(Hotkeys.MAP_MARKER_DELETE).binding().set(InputGesture.none());

        assertTrue(map.mousedown(new Widget.MouseDownEvent(new Coord(100, 100), 1)));
        assertTrue(recorder.waypoints.isEmpty(), "press must not record before release");
        assertTrue(map.mouseup(new Widget.MouseUpEvent(new Coord(100, 100), 1)));
        assertEquals(1, marks.getMarksForSegment(42).size());
        assertEquals(1, recorder.waypoints.size());
        assertEquals(42, recorder.waypoints.get(0).seg);
        assertEquals(new Coord(240, 360), recorder.waypoints.get(0).tc);
        assertEquals(2, recorder.recordingChecks, "both real event handlers consult recording state");
    }

    private Frame viewFrame(MapWnd window) throws Exception {
        Constructor<?> ctor = Class.forName("haven.MapWnd$ViewFrame").getDeclaredConstructor(MapWnd.class);
        ctor.setAccessible(true);
        Frame frame = (Frame) ctor.newInstance(window);
        frame.parent = window;
        frame.ui = ui;
        frame.c = new Coord(10, 20);
        frame.resize(new Coord(200, 200));
        return frame;
    }

    private void control(boolean down) throws Exception {
        ui.modctrl = down;
        field(NUI.class, "cachedModFlags").set(ui, -1);
    }

    private NMiniMap recordingMap(RecordingWindow recorder) throws Exception {
        SilentGameUI gui = allocate(SilentGameUI.class);
        gui.ui = ui;
        gui.lchild = recorder;
        ui.gui = gui;
        Constructor<LabeledMarkService> ctor = LabeledMarkService.class.getDeclaredConstructor(
                NGameUI.class, String.class, String.class);
        ctor.setAccessible(true);
        marks = ctor.newInstance(null, "task8", tempDir.resolve("marks.json").toString());
        gui.labeledMarkService = marks;
        marks.addLabeledMark("q100", "Quarryartz", 100, 42, new Coord(240, 360), null);
        NMiniMap map = new NMiniMap(new Coord(200, 200), null);
        map.ui = ui;
        field(NMiniMap.class, "showQuarryartzIcons").set(map, true);
        MapFile file = allocate(MapFile.class);
        map.dloc = new MiniMap.Location(file.new Segment(42), new Coord(240, 360));
        map.sessloc = map.dloc;
        return map;
    }

    private static final class RecordingWindow extends Widget implements PathRecordable {
        final List<ForagerWaypoint> waypoints = new ArrayList<>();
        int recordingChecks;
        public boolean isRecording() { recordingChecks++; return true; }
        public void addWaypointToRecording(ForagerWaypoint waypoint) { waypoints.add(waypoint); }
        public ForagerPath getCurrentLoadedPath() { return null; }
    }

    // Only the chat/rendering boundary is suppressed; mark deletion uses the real service.
    private static final class SilentGameUI extends NGameUI {
        private SilentGameUI() { super("test", 0, "test", null); }
        @Override public void msg(String message, Color color) { }
    }

    private static final class BindingState {
        final HotkeyBinding binding;
        final InputGesture gesture;
        final PreferenceStore preferences;
        final String key;
        final String encoded;
        BindingState(HotkeyBinding binding) throws Exception {
            this.binding = binding;
            gesture = binding.current();
            preferences = (PreferenceStore) field(binding.getClass(), "preferences").get(binding);
            key = (String) field(binding.getClass(), "preferenceKey").get(binding);
            encoded = preferences.get(key, null);
        }
        void restore() {
            binding.set(gesture);
            preferences.set(key, encoded);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        return type.cast(unsafe.allocateInstance(type));
    }
}
