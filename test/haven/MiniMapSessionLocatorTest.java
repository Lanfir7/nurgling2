package haven;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MiniMapSessionLocatorTest {
    @Test
    void sessionLocatorDoesNotUseCachedGridUntilServerRefresh() {
        Session sess = new Session(new Transport.Playback(new StringReader("")), new Session.User("test"));
        MCache map = sess.glob.map;
        MCache.Grid grid = map.new Grid(Coord.z);
        grid.id = 100;
        grid.serverFresh = true;
        map.grids.put(grid.gc, grid);

        MapFile file = new MapFile(new ResCache.TestCache(), "");
        MapFile.Segment seg = file.new Segment(10);
        file.lock.writeLock().lock();
        try {
            file.segments.put(seg.id, seg);
            file.gridinfo.put(grid.id, new MapFile.GridInfo(grid.id, seg.id, Coord.of(20, 20)));
        } finally {
            file.lock.writeLock().unlock();
        }

        MiniMap.SessionLocator locator = new MiniMap.SessionLocator(sess);
        locator.locate(file);

        grid.serverFresh = false;

        assertThrows(Loading.class, () -> locator.locate(file));
    }
}
