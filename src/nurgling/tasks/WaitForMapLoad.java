package nurgling.tasks;

import haven.MCache;
import nurgling.NGameUI;
import nurgling.areas.NGlobalCoord;

import java.util.ArrayList;

public class WaitForMapLoad extends NTask {
    private final NGameUI gui;

    NGlobalCoord coord;

    public WaitForMapLoad(NGameUI gui, NGlobalCoord coord) {
        this.gui = gui;
        this.coord = coord;
    }

    @Override
    public boolean check() {
        boolean canContinue = false;
        // Синхронизируем доступ к grids для безопасного создания копии
        ArrayList<MCache.Grid> gridsCopy;
        synchronized(gui.map.glob.map.grids) {
            gridsCopy = new ArrayList<>(gui.map.glob.map.grids.values());
        }
        for (MCache.Grid grid : gridsCopy) {
            if(this.coord.getGridId()==0)
                return true;
            if (grid.id == this.coord.getGridId()) {
                for(MCache.Grid.Cut cut : grid.cuts) {
                    canContinue = cut.mesh.isReady() && cut.fo.isReady();
                }
                return canContinue;
            }
        }
        return false;
    }
}
