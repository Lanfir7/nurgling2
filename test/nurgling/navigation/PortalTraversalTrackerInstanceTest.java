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
    void onlyConfirmedSurfaceExitResourcesClassifyAsSurface() {
        assertTrue(PortalTraversalTracker.isSurfaceExitPortal("gfx/tiles/ridges/cavein22"));
        assertTrue(PortalTraversalTracker.isSurfaceExitPortal("gfx/terobjs/minehole"));
        assertTrue(PortalTraversalTracker.isSurfaceExitPortal("gfx/terobjs/arch/thatchedhut"));
        assertFalse(PortalTraversalTracker.isSurfaceExitPortal("gfx/terobjs/ladder"));
    }
}
