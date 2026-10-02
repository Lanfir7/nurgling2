package nurgling.tasks;

import haven.Coord;
import haven.Coord2d;
import haven.MCache;
import nurgling.NGameUI;
import nurgling.NUtils;

public class WaitForMapLoadNoCoord extends NTask  {
    private final NGameUI gui;

    public WaitForMapLoadNoCoord(NGameUI gui) {
        this.gui = gui;
        this.infinite = false;
        this.maxCounter = 1800;
    }

    @Override
    public boolean check() {
        if(NUtils.player() == null) {
            return false;
        }

        if (NUtils.player().rc == null) {
            return false;
        }

        Coord2d rc = NUtils.player().rc;
        Coord tc = rc.div(MCache.tilesz).floor();
        Coord gc = tc.div(NUtils.getGameUI().ui.sess.glob.map.cmaps);

        if(NUtils.getGameUI().ui.sess.glob.map.grids.get(gc) == null) {
            return false;
        }

        MCache.Grid grid = gui.map.glob.map.getgridt(tc);
        /* A grid is 100×100 tiles, but meshes are built only for cuts the camera
         * actually draws. The cut under the player is the one the world shows. */
        Coord cc = tc.sub(grid.ul).div(MCache.cutsz);
        for (MCache.Grid.Cut cut : grid.cuts) {
            if (cut.cc.equals(cc))
                return cut.mesh.isReady() && cut.fo.isReady();
        }
        return false;
    }
}
