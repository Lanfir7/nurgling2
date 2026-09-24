package nurgling.navigation;

import haven.Area;
import haven.Coord;
import nurgling.areas.NArea;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaNavigationInstanceTest {
    @Test
    void cachedSurfaceAreaIsNotLocallyReachableFromBasement() {
        ChunkNavManager manager = new ChunkNavManager();
        manager.setCurrentInstanceId(42L);
        ChunkNavData surface = new ChunkNavData(123L);
        surface.instanceId = ChunkNavManager.SURFACE_INSTANCE;
        manager.getGraph().addChunk(surface);

        NArea area = new NArea("Boards");
        area.space = new NArea.Space();
        area.space.space.put(123L, new NArea.VArea(new Area(Coord.z, Coord.of(5, 5))));

        assertFalse(AreaNavigationHelper.isAreaOnCurrentInstance(area, manager));
        manager.setCurrentInstanceId(ChunkNavManager.SURFACE_INSTANCE);
        assertTrue(AreaNavigationHelper.isAreaOnCurrentInstance(area, manager));

        ChunkNavData basement = new ChunkNavData(124L);
        basement.instanceId = 42L;
        manager.getGraph().addChunk(basement);
        area.space.space.put(124L, new NArea.VArea(new Area(Coord.z, Coord.of(5, 5))));
        manager.setCurrentInstanceId(42L);
        assertTrue(AreaNavigationHelper.isAreaOnCurrentInstance(area, manager));
    }
}
