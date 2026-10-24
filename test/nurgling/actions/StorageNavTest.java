package nurgling.actions;

import haven.Coord;
import nurgling.db.dao.StorageItemDao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StorageNavTest {
    private static final class Route implements StorageNav.Navigation<String> {
        final List<String> calls = new ArrayList<>();
        String visible;
        String arrival;
        boolean localWorks;
        boolean chunkWorks;
        long grid;
        Coord coord;

        @Override public String find(String hash) {
            calls.add("find:" + hash);
            return visible;
        }

        @Override public boolean local(long gridId, Coord local) {
            calls.add("local");
            grid = gridId;
            coord = local;
            return localWorks;
        }

        @Override public boolean chunk(long gridId, Coord local) {
            calls.add("chunk");
            grid = gridId;
            coord = local;
            return chunkWorks;
        }

        @Override public String await(String hash) {
            calls.add("await:" + hash);
            return arrival;
        }
    }

    @Test void loadedGridPathfinderFailureFallsBackToChunkRoute() throws Exception {
        Route route = new Route();
        route.chunkWorks = true;
        route.arrival = "selected-chest";
        StorageItemDao.LocatedItem row = new StorageItemDao.LocatedItem(
                "Flour", "selected-chest", 741L, "(400, 250)");
        assertEquals("selected-chest", StorageNav.reach(
                row.container, row.gridId, row.coord, () -> false, route));
        assertEquals(741L, route.grid);
        assertEquals(new Coord(400, 250), route.coord);
        assertEquals(List.of("find:selected-chest", "local", "chunk", "await:selected-chest"), route.calls);
    }

    @Test void successfulLocalWalkStillNeedsHashAndCanUsePortalRoute() throws Exception {
        Route route = new Route();
        route.localWorks = true;
        route.chunkWorks = true;
        assertNull(StorageNav.reach("selected-chest", 19, "(1,2)", () -> false, route));
        assertEquals(List.of("find:selected-chest", "local", "await:selected-chest",
                "chunk", "await:selected-chest"), route.calls);
    }

    @Test void visibleHashNeedsNoCoordinatesOrNavigation() throws Exception {
        Route route = new Route();
        route.visible = "selected-chest";
        assertEquals("selected-chest", StorageNav.reach(
                "selected-chest", 0, "stale", () -> false, route));
        assertEquals(List.of("find:selected-chest"), route.calls);
    }

    @Test void invalidCoordinateAndCancellationNeverLaunchNavigation() throws Exception {
        for (String bad : List.of("(NaN,1)", "(Infinity,1)", "(100001,0)", "bad", "(1,2,3)")) {
            Route route = new Route();
            assertNull(StorageNav.reach("selected-chest", 19, bad, () -> false, route));
            assertEquals(List.of("find:selected-chest"), route.calls);
        }
        AtomicBoolean cancel = new AtomicBoolean();
        StorageNav.Navigation<String> cancelledDuringLocal = new StorageNav.Navigation<String>() {
            @Override public String find(String hash) { return null; }
            @Override public boolean local(long gridId, Coord local) {
                cancel.set(true);
                return false;
            }
            @Override public boolean chunk(long gridId, Coord local) {
                fail("cancelled navigation must not plan another route");
                return true;
            }
            @Override public String await(String hash) { return null; }
        };
        assertNull(StorageNav.reach("selected-chest", 19, "(1,2)", cancel::get, cancelledDuringLocal));
    }
}
