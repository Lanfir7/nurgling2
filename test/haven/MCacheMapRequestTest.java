package haven;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCacheMapRequestTest {
    @Test
    void missingGridRequestExpiresAfterRetryLimit() throws Exception {
        MCache map = new MCache(new Session(new TestTransport(), null));
        Coord grid = Coord.of(4, 7);

        map.request(grid);
        setRetries(map, grid, 4);
        map.sendreqs();

        assertFalse(pending(map, grid));
    }

    @Test
    void invalidatedLoadedGridKeepsLateResponseEligibleAfterRetryLimit() throws Exception {
        MCache map = new MCache(new Session(new TestTransport(), null));
        Coord grid = Coord.of(4, 7);
        MCache.Grid loaded = map.new Grid(grid);
        map.grids.put(grid, loaded);

        map.invalidate(grid);
        setRetries(map, grid, 4);
        map.sendreqs();

        assertTrue(pending(map, grid), "server-invalidated grid refresh must remain eligible for a late response");
        map.mapdata2(emptyGridData(grid));
        assertFalse(pending(map, grid));
        assertSame(loaded, map.grids.get(grid));
    }

    @Test
    void repeatedInvalidationPromotesPendingRequestForLoadedGrid() throws Exception {
        MCache map = new MCache(new Session(new TestTransport(), null));
        Coord grid = Coord.of(4, 7);

        map.request(grid);
        map.grids.put(grid, map.new Grid(grid));
        map.invalidate(grid);
        setRetries(map, grid, 4);
        map.sendreqs();

        assertTrue(pending(map, grid));
    }

    private static Message emptyGridData(Coord grid) {
        MessageBuf write = new MessageBuf();
        write.addcoord(grid);
        write.adduint8(1);
        return new MessageBuf(write.wbuf, 0, write.size());
    }

    private static void setRetries(MCache map, Coord grid, int retries) throws Exception {
        Object request = requests(map).get(grid);
        Field reqs = request.getClass().getDeclaredField("reqs");
        Field lastreq = request.getClass().getDeclaredField("lastreq");
        reqs.setAccessible(true);
        lastreq.setAccessible(true);
        reqs.setInt(request, retries);
        lastreq.setLong(request, 0);
    }

    private static boolean pending(MCache map, Coord grid) throws Exception {
        return requests(map).containsKey(grid);
    }

    @SuppressWarnings("unchecked")
    private static Map<Coord, Object> requests(MCache map) throws Exception {
        Field req = MCache.class.getDeclaredField("req");
        req.setAccessible(true);
        return (Map<Coord, Object>) req.get(map);
    }

    private static class TestTransport implements Transport {
        public void close() {
        }

        public void queuemsg(PMessage msg) {
        }

        public void send(PMessage msg) {
        }

        public Transport add(Callback cb) {
            return this;
        }
    }
}
