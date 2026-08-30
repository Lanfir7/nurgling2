package nurgling.navigation;

import nurgling.tasks.GateDetector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThatchedHutPortalTest {
    private static final String EXTERIOR = "gfx/terobjs/arch/thatchedhut";
    private static final String INTERIOR_DOOR = "gfx/terobjs/arch/thatchedhut-door";

    @Test
    void thatchedHutResourcesFormTraversablePortalPair() {
        assertEquals(INTERIOR_DOOR, GateDetector.getDoorPair(EXTERIOR));
        assertEquals(EXTERIOR, GateDetector.getDoorPair(INTERIOR_DOOR));
    }

    @Test
    void thatchedHutExteriorIsClassifiedAsDoorPortal() {
        assertTrue(ChunkPortal.isBuildingExterior(EXTERIOR));
        assertEquals(ChunkPortal.PortalType.DOOR, ChunkPortal.classifyPortal(EXTERIOR));
    }

    @Test
    void traversalTrackerRecognizesBothSidesOfThatchedHut() throws Exception {
        PortalTraversalTracker tracker = new PortalTraversalTracker(null, null, null);
        Method isPortalGob = PortalTraversalTracker.class.getDeclaredMethod("isPortalGob", String.class);
        isPortalGob.setAccessible(true);

        assertTrue((Boolean) isPortalGob.invoke(tracker, EXTERIOR));
        assertTrue((Boolean) isPortalGob.invoke(tracker, INTERIOR_DOOR));
    }

    @Test
    void thatchedHutEntranceUsesDoorSideInsteadOfBlockedCenter() throws Exception {
        PortalTraversalTracker tracker = new PortalTraversalTracker(null, null, null);
        Method getBuildingDoorOffset = PortalTraversalTracker.class
                .getDeclaredMethod("getBuildingDoorOffset", String.class);
        getBuildingDoorOffset.setAccessible(true);

        assertEquals(2.0, (Double) getBuildingDoorOffset.invoke(tracker, EXTERIOR));
    }

    @Test
    void executorApproachesThatchedHutAsBuildingEntrance() throws Exception {
        ChunkNavManager manager = new ChunkNavManager();
        ChunkNavExecutor executor = new ChunkNavExecutor(null, null, manager);

        Method isBuildingGob = ChunkNavExecutor.class.getDeclaredMethod("isBuildingGob", String.class);
        isBuildingGob.setAccessible(true);
        Method getBuildingAccessOffset = ChunkNavExecutor.class
                .getDeclaredMethod("getBuildingAccessOffset", String.class);
        getBuildingAccessOffset.setAccessible(true);

        assertTrue((Boolean) isBuildingGob.invoke(executor, EXTERIOR));
        assertEquals(2.0, (Double) getBuildingAccessOffset.invoke(executor, EXTERIOR));
    }
}
