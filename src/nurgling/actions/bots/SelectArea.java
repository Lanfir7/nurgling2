package nurgling.actions.bots;

import haven.*;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.areas.NArea;
import nurgling.overlays.NCustomBauble;

import java.awt.image.BufferedImage;

public class SelectArea implements Action {

    public SelectArea() {

    }

    public SelectArea(BufferedImage image) {
        this.image = image;
    }

    public SelectArea(BufferedImage image, BufferedImage Spr) {
        this.image = image;
        this.spr = Spr;
    }
    BufferedImage image = null;
    BufferedImage spr = null;
    public NArea.Space result;

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        if (gui == null || gui.map == null || gui.ui == null) {
            return Results.FAIL();
        }
        NMapView map = (NMapView) gui.map;

        if (!map.isAreaSelectionMode.get()) {
            Gob player = NUtils.player();
            map.isAreaSelectionMode.set(true);
            if(image!=null && player!=null)
            {
                player.addcustomol(new NCustomBauble(player,image, spr, map.isAreaSelectionMode));
            }
            nurgling.tasks.SelectArea sa;
            gui.ui.core.addTask(sa = new nurgling.tasks.SelectArea(gui));
            if (sa.getResult() != null) {
                result = sa.getResult();
            }
        }
        else
        {
            return Results.FAIL();
        }
        return Results.SUCCESS();
    }

    public Pair<Coord2d,Coord2d> getRCArea() {
        NGameUI gui = NUtils.getGameUI();
        if (result == null || gui == null || gui.map == null) {
            return null;
        }

        Coord begin = null;
        Coord end = null;
        for (Long id : result.space.keySet()) {
            MCache.Grid grid = gui.map.glob.map.findGrid(id);
            if (grid == null) {
                continue;
            }
            Area area = result.space.get(id).area;
            Coord b = area.ul.add(grid.ul);
            Coord e = area.br.add(grid.ul);
            begin = (begin != null) ? new Coord(Math.min(begin.x, b.x), Math.min(begin.y, b.y)) : b;
            end = (end != null) ? new Coord(Math.max(end.x, e.x), Math.max(end.y, e.y)) : e;
        }
        if (begin != null)
            return new Pair<Coord2d, Coord2d>(begin.mul(MCache.tilesz), end.sub(1, 1).mul(MCache.tilesz).add(MCache.tilesz));
        return null;
    }

}
