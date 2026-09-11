package nurgling.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalTraversalTrackerInstanceTest {
    @Test
    void confirmedSurfaceExitOverridesAndRestampsStaleDestinationInstance() {
        ChunkNavData destination = new ChunkNavData(42L);
        destination.instanceId = 9001L;
        destination.layer = "outside";

        long assigned = PortalTraversalTracker.destinationInstanceAfterTraversal(
                destination, 42L, "gfx/tiles/ridges/cavein22");
        PortalTraversalTracker.stampDestinationInstance(destination, assigned);

        assertEquals(ChunkNavManager.SURFACE_INSTANCE, assigned);
        assertEquals(ChunkNavManager.SURFACE_INSTANCE, destination.instanceId);
    }

    @Test
    void ladderTraversalPreservesExistingCaveInstance() {
        ChunkNavData destination = new ChunkNavData(42L);
        destination.instanceId = 9001L;
        destination.layer = "outside";

        long assigned = PortalTraversalTracker.destinationInstanceAfterTraversal(
                destination, 42L, "gfx/terobjs/ladder");
        PortalTraversalTracker.stampDestinationInstance(destination, assigned);

        assertEquals(9001L, assigned);
        assertEquals(9001L, destination.instanceId);
    }

    @Test
    void confirmedCaveEntryOverridesPrematureSurfaceStamp() {
        ChunkNavData destination = new ChunkNavData(42L);
        destination.instanceId = ChunkNavManager.SURFACE_INSTANCE;
        destination.layer = "outside";

        long assigned = PortalTraversalTracker.destinationInstanceAfterTraversal(
                destination, 42L, "gfx/tiles/ridges/caveout");
        PortalTraversalTracker.stampDestinationInstance(destination, assigned);

        assertEquals(42L, assigned);
        assertEquals(42L, destination.instanceId);
    }

    @Test
    void portalRestampClearsDirectionalCrossInstanceNeighbors() {
        ChunkNavGraph graph = new ChunkNavGraph();
        ChunkNavData destination = new ChunkNavData(42L);
        destination.instanceId = 42L;
        destination.layer = "outside";
        ChunkNavData staleSurface = new ChunkNavData(43L);
        staleSurface.instanceId = ChunkNavManager.SURFACE_INSTANCE;
        staleSurface.layer = "outside";
        destination.neighborEast = staleSurface.gridId;
        staleSurface.neighborWest = destination.gridId;
        destination.connectedChunks.add(staleSurface.gridId);
        staleSurface.connectedChunks.add(destination.gridId);
        graph.addChunk(destination);
        graph.addChunk(staleSurface);

        PortalTraversalTracker.removeCrossInstanceConnections(graph, destination);

        assertEquals(-1L, destination.neighborEast);
        assertEquals(-1L, staleSurface.neighborWest);
        assertFalse(destination.connectedChunks.contains(staleSurface.gridId));
        assertFalse(staleSurface.connectedChunks.contains(destination.gridId));
    }

    @Test
    void onlyConfirmedSurfaceExitResourcesClassifyAsSurface() {
        assertTrue(PortalTraversalTracker.isSurfaceExitPortal("gfx/tiles/ridges/cavein22"));
        assertTrue(PortalTraversalTracker.isSurfaceExitPortal("gfx/terobjs/minehole"));
        assertTrue(PortalTraversalTracker.isSurfaceExitPortal("gfx/terobjs/arch/thatchedhut"));
        assertFalse(PortalTraversalTracker.isSurfaceExitPortal("gfx/terobjs/ladder"));
    }
}
