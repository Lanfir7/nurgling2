package haven;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertSame;

class MapFileSegmentGridRefTest {
    @Test
    void coordinateReferenceFollowsReplacementGridId() throws Exception {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        MapFile.Segment segment = file.new Segment(1L);
        Coord sc = Coord.of(7, 9);
        Method include = MapFile.Segment.class.getDeclaredMethod("include", long.class, Coord.class);
        include.setAccessible(true);

        file.lock.writeLock().lock();
        try {
            MapFile.Segment.ByCoord byCoord = (MapFile.Segment.ByCoord) segment.grid(sc);

            include.invoke(segment, 100L, sc);
            MapFile.Grid first = new MapFile.Grid(100L, null, null, null, 0L);
            byCoord.cur.loaded = first;
            assertSame(first, byCoord.get());

            MapFile.Segment.Cached replacement = (MapFile.Segment.Cached) segment.grid(200L);
            MapFile.Grid second = new MapFile.Grid(200L, null, null, null, 0L);
            replacement.loaded = second;
            include.invoke(segment, 200L, sc);

            assertSame(second, byCoord.get());
        } finally {
            file.lock.writeLock().unlock();
        }
    }
}
