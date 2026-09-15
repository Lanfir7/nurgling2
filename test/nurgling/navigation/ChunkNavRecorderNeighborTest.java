package nurgling.navigation;

import haven.Coord;
import haven.MCache;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkNavRecorderNeighborTest {
    @Test
    void loadedSameInstanceGridsBecomeReciprocalNeighborsFromMCacheCoordinates() {
        MCache cache = new MCache(null);
        MCache.Grid westGrid = cache.new Grid(Coord.of(20, 30));
        MCache.Grid eastGrid = cache.new Grid(Coord.of(21, 30));
        westGrid.id = 101L;
        eastGrid.id = 102L;

        ChunkNavGraph graph = new ChunkNavGraph();
        ChunkNavData west = chunk(westGrid);
        ChunkNavData east = chunk(eastGrid);
        graph.addChunk(west);
        graph.addChunk(east);

        ChunkNavRecorder.discoverLoadedNeighbors(graph, westGrid, west,
                Arrays.asList(westGrid, eastGrid));
        ChunkNavRecorder.discoverLoadedNeighbors(graph, eastGrid, east,
                Arrays.asList(westGrid, eastGrid));

        assertEquals(eastGrid.id, west.neighborEast);
        assertEquals(westGrid.id, east.neighborWest);
    }

    private static ChunkNavData chunk(MCache.Grid grid) {
        ChunkNavData data = new ChunkNavData(grid.id, grid.gc, grid.ul);
        data.instanceId = ChunkNavManager.SURFACE_INSTANCE;
        return data;
    }
}
