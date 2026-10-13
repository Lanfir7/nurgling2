package nurgling.navigation;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkNavGraphVillageTest {
    @Test
    void countsAreasPerConnectedPieceIncludingOneWayDoor() {
        ChunkNavGraph graph = new ChunkNavGraph();
        ChunkNavData yard = new ChunkNavData(1);
        ChunkNavData house = new ChunkNavData(2);
        ChunkNavData cellar = new ChunkNavData(3);
        ChunkNavData remote = new ChunkNavData(4);
        yard.instanceId = house.instanceId = 1;
        cellar.instanceId = 2;
        remote.instanceId = 3;
        yard.neighborEast = house.gridId; // An old recorded link has only one side.
        yard.neighborWest = remote.gridId; // A stale cross-instance neighbor must not join villages.
        ChunkPortal door = new ChunkPortal("door", "gfx/terobjs/cellardoor",
                ChunkPortal.PortalType.CELLAR, Coord.z);
        door.connectsToGridId = cellar.gridId;
        house.portals.add(door); // The cellar has no return portal recorded.
        graph.addChunk(yard);
        graph.addChunk(house);
        graph.addChunk(cellar);
        graph.addChunk(remote);

        Set<Long> village = graph.pieceWithMostAreas(Arrays.asList(
                Arrays.asList(1L, 2L), // A multi-grid area counts once.
                Collections.singletonList(3L),
                Collections.singletonList(4L)));
        assertEquals(Set.of(1L, 2L, 3L), village);
        assertEquals(Collections.emptySet(), graph.pieceWithMostAreas(
                Collections.singletonList(Collections.singletonList(999L))));
    }
}
