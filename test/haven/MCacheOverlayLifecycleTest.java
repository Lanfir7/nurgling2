package haven;

import haven.render.RenderTree;
import nurgling.NConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MCacheOverlayLifecycleTest {
    @BeforeAll
    static void setUpSettings() {
        NConfig.getGlobalInstance();
    }

    private static class OverlayId implements MCache.OverlayInfo {
        final AtomicBoolean fail = new AtomicBoolean();

        public Collection<String> tags() {return Arrays.asList("show");}
        public Material mat() {
            if(fail.get())
                throw new IllegalStateException("material unavailable");
            return new Material();
        }
    }

    private static class Node implements RenderTree.Node, Disposable {
        volatile boolean disposed;
        volatile int disposals;
        public void dispose() {disposed = true; disposals++;}
    }

    private static class Fixture {
        final MCache map = new MCache(null);
        final MCache.Grid grid = map.new Grid(Coord.z);
        final MCache.Grid.Cut cut = grid.cuts[0];

        Fixture() {
            map.grids.put(Coord.z, grid);
        }

        MapView view() throws Exception {
            Glob glob = allocate(Glob.class);
            field(Glob.class, "map").set(glob, map);
            MapView view = allocate(MapView.class);
            field(MapView.class, "glob").set(view, glob);
            return view;
        }

        RenderTree.Slot show(MapView view, OverlayId id) {
            return new RenderTree().add(view.new Overlay(id));
        }

        MapMesh retiredMesh() throws Exception {
            Constructor<MapMesh> ctor = MapMesh.class.getDeclaredConstructor(MCache.class, Coord.class,
                    Coord.class, Random.class);
            ctor.setAccessible(true);
            MapMesh mesh = ctor.newInstance(map, Coord.z, MCache.cutsz, new Random(1));
            Node part = new Node();
            parts(mesh).add(part);
            cut.retiremesh(mesh);
            assertFalse(part.disposed, "stale overlay must initially pin the terrain mesh");
            return mesh;
        }
    }

    @Test
    void removingSelectionClearsNullEntriesAndHundredsOfRetiredMeshes() throws Exception {
        Fixture f = new Fixture();
        OverlayId selection = new OverlayId();
        RenderTree.Slot slot = f.show(f.view(), selection);
        f.cut.ols.put(selection, null);
        f.cut.olols.put(selection, null);
        f.cut.olmv = 83;
        f.cut.meshver = 426;
        List<MapMesh> meshes = new ArrayList<>();
        for(int i = 0; i < 300; i++)
            meshes.add(f.retiredMesh());
        assertEquals(300, oldmeshes(f.cut).size());

        slot.remove();

        assertFalse(f.cut.ols.containsKey(selection));
        assertFalse(f.cut.olols.containsKey(selection));
        assertTrue(oldmeshes(f.cut).isEmpty());
        for(MapMesh mesh : meshes)
            assertTrue(parts(mesh).get(0) instanceof Node && ((Node)parts(mesh).get(0)).disposed);
    }

    @Test
    void lastOfTwoViewsReleasesSharedOverlay() throws Exception {
        Fixture f = new Fixture();
        OverlayId id = new OverlayId();
        RenderTree.Slot first = f.show(f.view(), id);
        RenderTree.Slot second = f.show(f.view(), id);
        Node base = new Node();
        f.cut.ols.put(id, base);
        f.cut.olols.put(id, null);

        first.remove();
        assertSame(base, f.cut.ols.get(id));
        assertFalse(base.disposed);

        second.add(new RenderTree.Node() {
            public void removed(RenderTree.Slot slot) {
                assertFalse(base.disposed, "children must detach before cached geometry is disposed");
            }
        });
        second.remove();
        assertFalse(f.cut.ols.containsKey(id));
        assertTrue(base.disposed);
    }

    @Test
    void duplicateAttachmentKeepsOriginalOwnerUntilItsSlotLeaves() throws Exception {
        Fixture f = new Fixture();
        OverlayId id = new OverlayId();
        MapView.Overlay overlay = f.view().new Overlay(id);
        RenderTree.Slot original = new RenderTree().add(overlay);
        Node cached = new Node();
        f.cut.ols.put(id, cached);

        assertThrows(IllegalStateException.class, () -> new RenderTree().add(overlay));
        assertSame(cached, f.cut.ols.get(id));
        assertFalse(cached.disposed);

        original.remove();
        assertFalse(f.cut.ols.containsKey(id));
        assertEquals(1, cached.disposals);
    }

    @Test
    void removingOneIdPreservesOtherOrdinaryAndCustomPins() throws Exception {
        Fixture f = new Fixture();
        OverlayId firstId = new OverlayId(), secondId = new OverlayId();
        RenderTree.Slot first = f.show(f.view(), firstId);
        RenderTree.Slot second = f.show(f.view(), secondId);
        Node firstBase = new Node(), firstOutline = new Node(), secondBase = new Node(), custom = new Node();
        f.cut.ols.put(firstId, firstBase);
        f.cut.olols.put(firstId, firstOutline);
        f.cut.ols.put(secondId, secondBase);
        f.cut.nols.put(17, custom);
        f.cut.olmv = f.cut.nolmv = 0;
        f.cut.meshver = 1;
        MapMesh old = f.retiredMesh();

        first.remove();
        assertTrue(firstBase.disposed && firstOutline.disposed);
        assertFalse(secondBase.disposed);
        assertFalse(custom.disposed);
        assertEquals(1, oldmeshes(f.cut).size());

        second.remove();
        assertTrue(secondBase.disposed);
        assertEquals(1, oldmeshes(f.cut).size());
        f.cut.invalidateNol(17);
        assertTrue(oldmeshes(f.cut).isEmpty());
        assertTrue(((Node)parts(old).get(0)).disposed);
    }

    @Test
    void removalCancelsPendingBuildAndDisposesRetiredNodesOnce() throws Exception {
        Fixture f = new Fixture();
        OverlayId id = new OverlayId();
        RenderTree.Slot slot = f.show(f.view(), id);
        Node cached = new Node(), retired = new Node();
        f.cut.ols.put(id, cached);
        f.cut.olols.put(id, cached);
        retired(f.cut).put(id, new ArrayList<>(Arrays.asList(retired, retired)));
        builds(f.cut).put(id, Defer.later(() -> null));
        f.cut.olmv = 0;
        f.cut.meshver = 1;
        MapMesh mesh = f.retiredMesh();

        slot.remove();

        assertFalse(builds(f.cut).containsKey(id));
        assertFalse(retired(f.cut).containsKey(id));
        assertEquals(1, cached.disposals);
        assertEquals(1, retired.disposals);
        assertTrue(oldmeshes(f.cut).isEmpty());
        assertTrue(((Node)parts(mesh).get(0)).disposed);
    }

    @Test
    void runningCancelledBuildKeepsTerrainAliveUntilWorkerReleasesIt() throws Exception {
        Fixture f = new Fixture();
        OverlayId id = new OverlayId();
        RenderTree.Slot slot = f.show(f.view(), id);
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        Node built = new Node();
        Method build = MCache.Grid.Cut.class.getDeclaredMethod("build", Object.class, Defer.Callable.class);
        build.setAccessible(true);
        Defer.Callable<RenderTree.Node[]> work = () -> {
            started.countDown();
            while(finish.getCount() > 0) {
                try {finish.await();} catch(InterruptedException ignored) {}
            }
            return new RenderTree.Node[] {built};
        };
        synchronized(f.cut) {
            assertThrows(InvocationTargetException.class, () -> build.invoke(f.cut, id, work));
        }
        assertTrue(started.await(3, TimeUnit.SECONDS));
        f.cut.olmv = 0;
        f.cut.meshver = 1;
        MapMesh mesh = f.retiredMesh();
        Node part = (Node)parts(mesh).get(0);
        MapMesh later;
        try {
            slot.remove();
            assertFalse(part.disposed, "the worker still uses the old terrain mesh");
            later = f.retiredMesh();
            assertFalse(((Node)parts(later).get(0)).disposed);
            assertEquals(2, oldmeshes(f.cut).size());
        } finally {
            finish.countDown();
        }
        awaitDisposed(built);
        awaitDisposed(part);
        awaitDisposed((Node)parts(later).get(0));
        assertEquals(1, built.disposals);
        assertTrue(oldmeshes(f.cut).isEmpty());
    }

    @Test
    void disposingGridCutWaitsForRunningOverlayBuild() throws Exception {
        Fixture f = new Fixture();
        OverlayId id = new OverlayId();
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        Node built = new Node();
        Method build = MCache.Grid.Cut.class.getDeclaredMethod("build", Object.class, Defer.Callable.class);
        build.setAccessible(true);
        Defer.Callable<RenderTree.Node[]> work = () -> {
            started.countDown();
            while(finish.getCount() > 0) {
                try {finish.await();} catch(InterruptedException ignored) {}
            }
            return new RenderTree.Node[] {built};
        };
        synchronized(f.cut) {
            assertThrows(InvocationTargetException.class, () -> build.invoke(f.cut, id, work));
        }
        assertTrue(started.await(3, TimeUnit.SECONDS));
        MapMesh mesh = f.retiredMesh();
        Node part = (Node)parts(mesh).get(0);
        try {
            f.cut.dispose();
            assertFalse(part.disposed);
        } finally {
            finish.countDown();
        }
        awaitDisposed(built);
        awaitDisposed(part);
        assertTrue(oldmeshes(f.cut).isEmpty());
    }

    @Test
    void failedAttachmentRollsBackAndRepeatSelectionCanAttachAgain() throws Exception {
        Fixture f = new Fixture();
        OverlayId id = new OverlayId();
        id.fail.set(true);
        f.cut.ols.put(id, null);
        f.cut.olmv = 0;
        f.cut.meshver = 1;
        MapMesh old = f.retiredMesh();
        MapView view = f.view();

        assertThrows(IllegalStateException.class, () -> f.show(view, id));
        assertFalse(f.cut.ols.containsKey(id));
        assertTrue(((Node)parts(old).get(0)).disposed);

        id.fail.set(false);
        for(int i = 0; i < 20; i++) {
            RenderTree.Slot slot = f.show(view, id);
            f.cut.ols.put(id, null);
            slot.remove();
            assertFalse(f.cut.ols.containsKey(id));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<MapMesh> oldmeshes(MCache.Grid.Cut cut) throws Exception {
        return (List<MapMesh>)field(MCache.Grid.Cut.class, "oldmeshes").get(cut);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, List<RenderTree.Node>> retired(MCache.Grid.Cut cut) throws Exception {
        return (Map<Object, List<RenderTree.Node>>)field(MCache.Grid.Cut.class, "retired").get(cut);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Defer.Future<RenderTree.Node[]>> builds(MCache.Grid.Cut cut) throws Exception {
        return (Map<Object, Defer.Future<RenderTree.Node[]>>)field(MCache.Grid.Cut.class, "olbuild").get(cut);
    }

    @SuppressWarnings("unchecked")
    private static List<Disposable> parts(MapMesh mesh) throws Exception {
        return (List<Disposable>)field(MapMesh.class, "dparts").get(mesh);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(((Unsafe)field(Unsafe.class, "theUnsafe").get(null)).allocateInstance(type));
    }

    private static void awaitDisposed(Node node) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while(!node.disposed && System.nanoTime() < deadline)
            Thread.sleep(10);
        assertTrue(node.disposed);
    }
}
