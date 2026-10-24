package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.GameUI;
import haven.render.BufPipe;
import haven.render.States;
import nurgling.NGameUI;
import nurgling.NConfig;
import nurgling.NUI;
import nurgling.NUtils;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NMiniMapLayerTest {
    private final List<NMiniMap.Layer> registered = new ArrayList<>();
    private NConfig previousConfig;

    @BeforeEach
    void configureFonts() {
        previousConfig = NConfig.current;
        NConfig.current = new NConfig();
    }

    @AfterEach
    void clearLayers() {
        for(NMiniMap.Layer layer : registered)
            NMiniMap.removeLayer(layer);
        NConfig.current = previousConfig;
    }

    private void add(NMiniMap.Layer layer) {
        registered.add(layer);
        NMiniMap.addLayer(layer);
    }

    private static GOut canvas() {
        return new GOut(null, new BufPipe(), new Coord(100, 100));
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((Unsafe) field.get(null)).allocateInstance(type);
    }

    private static NMiniMap map(Class<? extends NMiniMap> type, NUI ui) throws Exception {
        NMiniMap map = allocate(type);
        map.sz = new Coord(80, 60);
        map.ui = ui;
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends NMiniMap> worldMapType() throws Exception {
        return (Class<? extends NMiniMap>) Class.forName("haven.MapWnd$View").asSubclass(NMiniMap.class);
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

    @Test
    void registeredLayerReceivesEachActualMapAndUi() throws Exception {
        NUI a = ui("A"), b = ui("B"), prior = ThreadLocalUI.get();
        NMiniMap corner = map(NMiniMapWnd.Map.class, a);
        NMiniMap world = map(worldMapType(), b);
        assertNotSame(corner.ui, world.ui);
        List<NMiniMap> seen = new ArrayList<>();
        List<NUI> active = new ArrayList<>();
        List<String> characters = new ArrayList<>();
        add((map, g) -> {
            seen.add(map);
            active.add(NUtils.getUI());
            characters.add(map.ui.gui.chrid);
        });
        try {
            ThreadLocalUI.set(b);
            corner.drawLayers(canvas());
            ThreadLocalUI.set(a);
            world.drawLayers(canvas());
        } finally {
            if(prior == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(prior);
        }
        assertEquals(List.of(corner, world), seen);
        assertEquals(List.of(b, a), active);
        assertEquals(List.of("A", "B"), characters);
        assertSame(a, corner.ui);
        assertSame(b, world.ui);
    }

    @Test
    void eachLayerGetsClippedIndependentColorAndPipeState() throws Exception {
        NMiniMap map = map(NMiniMapWnd.Map.class, ui("A"));
        GOut parent = canvas().reclip(new Coord(10, 20), new Coord(40, 30));
        parent.chcolor(Color.GREEN);
        parent.usestate(new States.LineWidth(3));
        parent.tfbase = new BufPipe();
        new States.LineWidth(2).apply(parent.tfbase);
        parent.tforigin = new Coord(7, 8);
        List<GOut> children = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        List<Float> widths = new ArrayList<>(), tfWidths = new ArrayList<>();
        List<Coord> origins = new ArrayList<>();
        add((actual, child) -> {
            children.add(child);
            colors.add(child.getcolor());
            widths.add(child.state().get(States.linewidth).w);
            tfWidths.add(child.tfbase.get(States.linewidth).w);
            origins.add(new Coord(child.tforigin));
            child.chcolor(Color.RED);
            child.usestate(new States.LineWidth(9));
            new States.LineWidth(11).apply(child.tfbase);
            child.tforigin.x = 99;
        });
        add((actual, child) -> {
            children.add(child);
            colors.add(child.getcolor());
            widths.add(child.state().get(States.linewidth).w);
            tfWidths.add(child.tfbase.get(States.linewidth).w);
            origins.add(new Coord(child.tforigin));
        });
        map.drawLayers(parent);
        assertEquals(2, children.size());
        for(GOut child : children) {
            assertNotSame(parent, child);
            assertNotSame(parent.tfbase, child.tfbase);
            assertEquals(new Coord(10, 20), child.tx);
            assertEquals(new Coord(10, 20), child.ul);
            assertEquals(new Coord(50, 50), child.br);
        }
        assertEquals(List.of(Color.GREEN, Color.GREEN), colors);
        assertEquals(List.of(3f, 3f), widths);
        assertEquals(List.of(2f, 2f), tfWidths);
        assertEquals(List.of(new Coord(7, 8), new Coord(7, 8)), origins);
        assertEquals(Color.GREEN, parent.getcolor());
        assertEquals(3f, parent.state().get(States.linewidth).w);
        assertEquals(2f, parent.tfbase.get(States.linewidth).w);
        assertEquals(new Coord(7, 8), parent.tforigin);
    }

    @Test
    void failingLayerIsSkippedButFollowingLayersKeepDrawingAndReaddRetries() throws Exception {
        NMiniMap map = map(NMiniMapWnd.Map.class, ui("A"));
        AtomicInteger failures = new AtomicInteger(), following = new AtomicInteger();
        List<Color> followingColors = new ArrayList<>();
        List<Float> followingWidths = new ArrayList<>();
        NMiniMap.Layer bad = (actual, g) -> {
            failures.incrementAndGet();
            g.chcolor(Color.RED);
            g.usestate(new States.LineWidth(9));
            throw new IllegalStateException("test layer failure");
        };
        add(bad);
        add((actual, g) -> {
            following.incrementAndGet();
            followingColors.add(g.getcolor());
            followingWidths.add(g.state().get(States.linewidth).w);
        });
        GOut parent = canvas();
        parent.chcolor(Color.BLUE);
        parent.usestate(new States.LineWidth(4));
        map.drawLayers(parent);
        map.drawLayers(parent);
        assertEquals(1, failures.get());
        assertEquals(2, following.get());
        assertEquals(Color.BLUE, parent.getcolor());
        assertEquals(4f, parent.state().get(States.linewidth).w);

        NMiniMap.removeLayer(bad);
        NMiniMap.addLayer(bad);
        map.drawLayers(parent);
        assertEquals(2, failures.get(), "remove and re-add starts a new registration");
        assertEquals(3, following.get());
        assertEquals(List.of(Color.BLUE, Color.BLUE, Color.BLUE), followingColors);
        assertEquals(List.of(4f, 4f, 4f), followingWidths);
    }

    @Test
    void oldCallbackFailureCannotDisableFreshRegistration() throws Exception {
        NMiniMap map = map(NMiniMapWnd.Map.class, ui("A"));
        AtomicInteger calls = new AtomicInteger();
        NMiniMap.Layer[] holder = new NMiniMap.Layer[1];
        holder[0] = (actual, g) -> {
            if(calls.incrementAndGet() == 1) {
                NMiniMap.removeLayer(holder[0]);
                NMiniMap.addLayer(holder[0]);
                throw new NoClassDefFoundError("test old registration");
            }
        };
        add(holder[0]);
        map.drawLayers(canvas());
        map.drawLayers(canvas());
        assertEquals(2, calls.get());
    }

    @Test
    void nonfatalErrorDisablesOnlyItsLayer() throws Exception {
        NMiniMap map = map(NMiniMapWnd.Map.class, ui("A"));
        AtomicInteger bad = new AtomicInteger(), good = new AtomicInteger();
        add((actual, g) -> {
            bad.incrementAndGet();
            throw new AssertionError("plugin assertion");
        });
        add((actual, g) -> good.incrementAndGet());
        map.drawLayers(canvas());
        map.drawLayers(canvas());
        assertEquals(1, bad.get());
        assertEquals(2, good.get());
    }

    @Test
    void duplicateAddIsAtomicAndRemoveStopsDispatch() throws Exception {
        NMiniMap map = map(NMiniMapWnd.Map.class, ui("A"));
        AtomicInteger draws = new AtomicInteger();
        NMiniMap.Layer layer = (actual, g) -> draws.incrementAndGet();
        registered.add(layer);
        ExecutorService pool = Executors.newFixedThreadPool(8, task -> {
            Thread thread = new Thread(task, "test-map-layer-add");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for(int i = 0; i < 32; i++)
                futures.add(pool.submit(() -> {
                    go.await();
                    NMiniMap.addLayer(layer);
                    return null;
                }));
            go.countDown();
            for(Future<?> future : futures) future.get(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
        map.drawLayers(canvas());
        assertEquals(1, draws.get());
        NMiniMap.removeLayer(layer);
        map.drawLayers(canvas());
        assertEquals(1, draws.get());
    }

    @Test
    void fatalVmErrorsAreNotSwallowed() throws Exception {
        NMiniMap map = map(NMiniMapWnd.Map.class, ui("A"));
        add((actual, g) -> { throw new OutOfMemoryError("synthetic"); });
        assertThrows(OutOfMemoryError.class, () -> map.drawLayers(canvas()));
    }
}
