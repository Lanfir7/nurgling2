package nurgling.navigation;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedTilePathfinderBlockedStartTest {
    @Test
    void routesOutOfCellarWhenConstructionBlocksRecordedPlayerTile() {
        ChunkNavData cellar = new ChunkNavData();
        cellar.gridId = 1;
        cellar.layer = "cellar";
        cellar.instanceId = 1;
        for (int x = 44; x <= 49; x++) open(cellar, x, 50);
        for (int y = 47; y <= 50; y++) open(cellar, 44, y);

        ChunkNavData interior = new ChunkNavData();
        interior.gridId = 2;
        interior.layer = "inside";
        interior.instanceId = 2;
        open(interior, 51, 49);

        ChunkPortal stairs = new ChunkPortal("stairs", "gfx/terobjs/arch/cellarstairs",
                ChunkPortal.PortalType.CELLAR, new Coord(44, 47));
        stairs.connectsToGridId = interior.gridId;
        stairs.exitLocalCoord = new Coord(51, 49);
        cellar.portals.add(stairs);

        ChunkNavGraph graph = new ChunkNavGraph();
        graph.addChunk(cellar);
        graph.addChunk(interior);

        UnifiedTilePathfinder.UnifiedPath path = new UnifiedTilePathfinder(graph).findPath(
                cellar.gridId, new Coord(49, 47), interior.gridId, new Coord(51, 49));

        assertNotNull(path);
        assertTrue(path.reachable);
        assertEquals(new Coord(49, 47), path.steps.get(0).localCoord);
        assertTrue(path.steps.stream().anyMatch(step -> step.chunkId == interior.gridId && step.viaPortal));
    }

    @Test
    void doesNotBridgeLongGapsInRecordedWalkability() {
        ChunkNavData chunk = new ChunkNavData();
        chunk.gridId = 1;
        open(chunk, 20, 20);
        ChunkNavGraph graph = new ChunkNavGraph();
        graph.addChunk(chunk);

        assertNull(new UnifiedTilePathfinder(graph).findPath(1, new Coord(10, 10), 1, new Coord(20, 20)));
    }

    private static void open(ChunkNavData chunk, int x, int y) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                chunk.walkability[x * 2 + dx][y * 2 + dy] = 0;
            }
        }
    }
}
