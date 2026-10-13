package nurgling.overlays.map;

import haven.*;
import haven.render.BufPipe;
import nurgling.NConfig;
import nurgling.tools.ExploredArea;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Headless checks for immutable masks, bounded asynchronous rasterization and disk saves. */
public class ExplorationPerformanceTest {
    private static final Color COLOR = Color.YELLOW;
    private static final long SEGMENT = 17;

    static void check(boolean ok, String message) {if(!ok) throw new AssertionError(message);}

    static Object field(Class<?> type, Object target, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static final class Draws extends GOut {
        final List<Tex> images = new ArrayList<>();
        Draws(Coord size) {super(null, new BufPipe(), size);}
        @Override public void image(Tex image, Coord ul, Coord sz) {images.add(image);}
    }

    private static MiniMap.DisplayGrid visibleCell() throws Exception {
        // drawLayer only checks presence. No map-file fixture or game connection is required.
        Class<?> type = Class.forName("sun.misc.Unsafe");
        Object unsafe = field(type, null, "theUnsafe");
        return (MiniMap.DisplayGrid) type.getMethod("allocateInstance", Class.class)
                .invoke(unsafe, MiniMap.DisplayGrid.class);
    }

    private static void beginFrame(MinimapExploredAreaRenderer renderer) throws Exception {
        Method method = MinimapExploredAreaRenderer.class.getDeclaredMethod("beginFrame");
        method.setAccessible(true);
        method.invoke(renderer);
    }

    private static Draws draw(MinimapExploredAreaRenderer renderer, ExploredArea area, MiniMap map,
                              MiniMap.DisplayGrid cell, Coord first, int cells, long segment) throws Exception {
        beginFrame(renderer);
        MiniMap.DisplayGrid[] visible = new MiniMap.DisplayGrid[cells];
        Arrays.fill(visible, cell);
        Draws out = new Draws(map.sz);
        Method method = MinimapExploredAreaRenderer.class.getDeclaredMethod("drawLayer",
                GOut.class, MiniMap.class, ExploredArea.class, MiniMap.DisplayGrid[].class,
                Area.class, Coord.class, Coord.class, float.class, int.class, long.class,
                boolean.class, Color.class, long.class);
        method.setAccessible(true);
        method.invoke(renderer, out, map, area, visible, Area.sized(first, new Coord(cells, 1)),
                Coord.z, Coord.z, 1f, 0, segment, false, COLOR, ExploredArea.seq);
        return out;
    }

    private static Tex ready(MinimapExploredAreaRenderer renderer, ExploredArea area, MiniMap map,
                             MiniMap.DisplayGrid cell, Coord at, long segment) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime() < deadline) {
            Draws out = draw(renderer, area, map, cell, at, 1, segment);
            if(!out.images.isEmpty()) return out.images.get(0);
            Thread.sleep(1);
        }
        throw new AssertionError("Overlay did not complete at " + at);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory(Paths.get("build"), "exploration-perf-");
        Field localdir = Config.class.getDeclaredField("localdir");
        localdir.setAccessible(true);
        localdir.set(null, dir);
        Field haslocaldir = Config.class.getDeclaredField("haslocaldir");
        haslocaldir.setAccessible(true);
        haslocaldir.setBoolean(null, true);
        NConfig previous = NConfig.current;
        NConfig.current = new NConfig() {
            public String getExploredPath() {return dir.resolve("main.json").toString();}
            public String getSessionExploredPath() {return dir.resolve("session.json").toString();}
        };
        MinimapExploredAreaRenderer renderer = new MinimapExploredAreaRenderer();
        @SuppressWarnings("unchecked")
        AtomicReference<ExecutorService> executor =
                (AtomicReference<ExecutorService>) field(ExploredArea.class, null, "executorRef");
        ExecutorService io = executor.get();
        ThreadPoolExecutor raster = (ThreadPoolExecutor) field(MinimapExploredAreaRenderer.class, null, "rasterizer");
        CountDownLatch ioGate = new CountDownLatch(1), rasterGate = new CountDownLatch(1);
        MiniMap map = new MiniMap(new Coord(100000, 1000), null);
        MiniMap.DisplayGrid cell = visibleCell();
        try {
            ExploredArea area = new ExploredArea(null);
            setField(MinimapExploredAreaRenderer.class, renderer, "source", area);
            area.updateExploredTiles(new Coord(0, 0), new Coord(101, 1), SEGMENT);
            boolean[] a = area.getExploredMaskForGrid(Coord.z, SEGMENT, 0).mask;
            boolean[] b = area.getExploredMaskForGrid(new Coord(1, 0), SEGMENT, 0).mask;
            area.updateExploredTiles(new Coord(100, 0), new Coord(102, 1), SEGMENT);
            check(a == area.getExploredMaskForGrid(Coord.z, SEGMENT, 0).mask,
                    "Another grid invalidates unchanged mask");
            check(!b[1] && area.getExploredMaskForGrid(new Coord(1, 0), SEGMENT, 0).mask[1],
                    "Published mask mutated");
            boolean[] updated = area.getExploredMaskForGrid(new Coord(1, 0), SEGMENT, 0).mask;
            area.updateExploredTiles(new Coord(100, 0), new Coord(102, 1), SEGMENT);
            check(updated == area.getExploredMaskForGrid(new Coord(1, 0), SEGMENT, 0).mask,
                    "Unchanged exploration allocates masks");
            area.updateExploredTiles(new Coord(-1, -1), new Coord(0, 0), SEGMENT);
            check(area.getExploredMaskForGrid(new Coord(-1, -1), SEGMENT, 0).mask[9999],
                    "Negative grid boundary broken");

            raster.submit(() -> {try {rasterGate.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();}});
            long start = System.nanoTime();
            check(draw(renderer, area, map, cell, Coord.z, 1, SEGMENT).images.isEmpty(), "Raster ran inline");
            check(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(100), "Render waited for raster worker");
            rasterGate.countDown();
            Tex first = ready(renderer, area, map, cell, Coord.z, SEGMENT);
            check(((TexI) first).back.getRGB(0, 0) == COLOR.getRGB(), "Overlay color changed");
            check(((TexI) first).back.getRGB(0, 1) == 0, "Unexplored tile became opaque");
            for(int i = 0; i < 1000; i++)
                check(draw(renderer, area, map, cell, Coord.z, 1, SEGMENT).images.get(0) == first,
                        "Idle cache regenerates textures");

            area.updateExploredTiles(new Coord(1000, 0), new Coord(1300, 1), SEGMENT);
            CountDownLatch budgetGate = new CountDownLatch(1);
            raster.submit(() -> {try {budgetGate.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();}});
            try {
                for(int i = 10; i <= 12; i++)
                    check(draw(renderer, area, map, cell, new Coord(i, 0), 1, SEGMENT).images.isEmpty(),
                            "Queued texture was published early");
            } finally {budgetGate.countDown();}
            raster.submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(draw(renderer, area, map, cell, new Coord(10, 0), 3, SEGMENT).images.size() == 2,
                    "Texture upload budget not enforced");
            check(draw(renderer, area, map, cell, new Coord(10, 0), 3, SEGMENT).images.size() == 3,
                    "Budgeted upload never resumes");

            area.updateExploredTiles(new Coord(400, 0), new Coord(401, 1), SEGMENT);
            CountDownLatch staleGate = new CountDownLatch(1);
            raster.submit(() -> {try {staleGate.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();}});
            try {
                check(draw(renderer, area, map, cell, new Coord(4, 0), 1, SEGMENT).images.isEmpty(),
                        "Stale request already published");
                area.updateExploredTiles(new Coord(401, 0), new Coord(402, 1), SEGMENT);
            } finally {staleGate.countDown();}
            raster.submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(draw(renderer, area, map, cell, new Coord(4, 0), 1, SEGMENT).images.isEmpty(),
                    "Old raster result replaced the current mask");
            Tex current = ready(renderer, area, map, cell, new Coord(4, 0), SEGMENT);
            check(((TexI) current).back.getRGB(1, 0) == COLOR.getRGB(),
                    "Updated mask never reached texture");

            MinimapExploredAreaRenderer another = new MinimapExploredAreaRenderer();
            try {
                setField(MinimapExploredAreaRenderer.class, another, "source", area);
                check(ready(another, area, map, cell, Coord.z, SEGMENT) != first,
                        "Overlay texture leaks across minimap views");
            } finally {another.dispose();}
            area.updateExploredTiles(new Coord(0, 0), new Coord(1, 1), SEGMENT + 1);
            Tex otherSegment = ready(renderer, area, map, cell, Coord.z, SEGMENT + 1);
            check(otherSegment != first && ((TexI) otherSegment).back.getRGB(1, 0) == 0,
                    "Overlay cache leaks across segments");

            CountDownLatch queueGate = new CountDownLatch(1);
            raster.submit(() -> {try {queueGate.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();}});
            try {
                for(int i = 0; i < 700; i++)
                    draw(renderer, area, map, cell, new Coord(i, 5), 1, SEGMENT);
                check(((Map<?, ?>) field(MinimapExploredAreaRenderer.class, renderer, "overlayCache")).size() <= 512,
                        "Unbounded texture cache");
                check(raster.getQueue().size() <= 16, "Unbounded raster task queue");
            } finally {queueGate.countDown();}

            io.submit(() -> {try {ioGate.await();} catch(InterruptedException e) {Thread.currentThread().interrupt();}});
            Path path = dir.resolve("main.json");
            start = System.nanoTime();
            CompletableFuture<Void> save = area.saveAsync(path.toString());
            check(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(100) && !save.isDone(),
                    "UI waited for disk worker");
            area.updateExploredTiles(new Coord(2, 2), new Coord(3, 3), SEGMENT);
            check(!a[202], "Saving/updates mutate raster snapshot");
            ioGate.countDown();
            save.get(5, TimeUnit.SECONDS);
            ExploredArea second = new ExploredArea(null);
            second.updateExploredTiles(new Coord(3, 3), new Coord(4, 4), SEGMENT);
            second.saveAsync(path.toString()).get(5, TimeUnit.SECONDS);
            check(second.getExploredMaskForGrid(Coord.z, SEGMENT, 0).mask[202],
                    "Disk exploration lost during merge");
            area.saveAsync(path.toString()).get(5, TimeUnit.SECONDS);
            check(area.getExploredMaskForGrid(Coord.z, SEGMENT, 0).mask[303],
                    "Other client exploration not merged");
            byte[] before = Files.readAllBytes(path);
            CompletableFuture<Void> lockedSave;
            try(FileChannel lockChannel = FileChannel.open(Paths.get(path + ".lock"), StandardOpenOption.WRITE);
                    FileLock lock = lockChannel.lock()) {
                lockedSave = area.saveAsync(path.toString());
                Thread.sleep(50);
                check(!lockedSave.isDone() && Arrays.equals(before, Files.readAllBytes(path)),
                        "Lock contention damages file or fails instead of waiting");
            }
            lockedSave.get(5, TimeUnit.SECONDS);
            area.startSession();
            area.updateExploredTiles(new Coord(4, 4), new Coord(5, 5), SEGMENT);
            area.tick(1);
            io.submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(Files.readString(dir.resolve("session.json")).contains("true"),
                    "Session not saved asynchronously");
            area.endSession();
            io.submit(() -> {}).get(5, TimeUnit.SECONDS);
            check(!Files.exists(dir.resolve("session.json")), "Old queued save resurrects ended session");
            System.out.println("PASS: immutable masks, async raster/I/O, two uploads/frame, bounded queues, view/segment isolation, stale-result rejection");
        } finally {
            ioGate.countDown();
            rasterGate.countDown();
            renderer.dispose();
            NConfig.current = previous;
        }
    }
}
