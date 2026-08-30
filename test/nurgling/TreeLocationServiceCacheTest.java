package nurgling;

import haven.Coord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TreeLocationServiceCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void reusesSegmentResultUntilThatSegmentChanges() throws Exception {
        TreeLocation firstLocation = location(1L, 10, "Oak Tree");
        TreeLocation otherLocation = location(2L, 20, "Birch Tree");
        TreeLocationService service = service(firstLocation, otherLocation);

        List<TreeLocation> first = service.getTreeLocationsForSegment(1L);
        List<TreeLocation> second = service.getTreeLocationsForSegment(1L);

        assertSame(first, second);
        assertEquals(1, first.size());
    }

    @Test
    void removalInvalidatesOnlyAffectedSegment() throws Exception {
        TreeLocation removed = location(1L, 10, "Oak Tree");
        TreeLocation kept = location(2L, 20, "Birch Tree");
        TreeLocationService service = service(removed, kept);
        List<TreeLocation> beforeRemoved = service.getTreeLocationsForSegment(1L);
        List<TreeLocation> beforeKept = service.getTreeLocationsForSegment(2L);

        service.removeTreeLocation(removed.getLocationId());

        List<TreeLocation> afterRemoved = service.getTreeLocationsForSegment(1L);
        List<TreeLocation> afterKept = service.getTreeLocationsForSegment(2L);
        assertNotSame(beforeRemoved, afterRemoved);
        assertEquals(0, afterRemoved.size());
        assertSame(beforeKept, afterKept);
    }

    private TreeLocationService service(TreeLocation... locations) throws Exception {
        TreeLocationService service = (TreeLocationService) unsafe().allocateInstance(TreeLocationService.class);
        Map<String, TreeLocation> stored = new ConcurrentHashMap<>();
        for (TreeLocation location : locations)
            stored.put(location.getLocationId(), location);
        set(service, "treeLocations", stored);
        set(service, "treeLocationsBySegment", new ConcurrentHashMap<Long, List<TreeLocation>>());
        set(service, "lock", new ReentrantReadWriteLock());
        set(service, "dataFile", tempDir.resolve("tree-locations.json").toString());
        return service;
    }

    private static TreeLocation location(long segment, int tile, String name) {
        return new TreeLocation(segment, new Coord(tile, tile), name,
                "gfx/terobjs/trees/" + name.toLowerCase().replace(" tree", ""), 1, 100);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
