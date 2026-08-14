package nurgling.actions.bots;

import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.NContext;
import nurgling.navigation.ChunkNavManager;
import nurgling.tools.Context;

import nurgling.widgets.FoodContainer;

import java.util.ArrayList;

public class Eater implements Action {

    boolean oz = false;

    public Eater(boolean oz) {
        this.oz = oz;
    }

    public Eater() {
        this.oz = false;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        ArrayList<String> items = FoodContainer.getFoodNames();

        Pair<Coord2d,Coord2d> area = null;
        NArea nArea = NContext.findSpec(Specialisation.SpecName.eat.toString());
        if(nArea==null)
            nArea = NContext.findSpecGlobal(Specialisation.SpecName.eat.toString());
        if(nArea!=null)
            area = nArea.getRCArea();

        // Area found but grids not in MCache (player in mine/building).
        // Navigate to the area first via ChunkNav, then re-resolve coordinates.
        if (area == null && nArea != null) {
            ChunkNavManager chunkNav = (gui.map instanceof NMapView)
                ? ((NMapView)gui.map).getChunkNavManager() : null;
            if (chunkNav != null && chunkNav.isInitialized()) {
                Results nav = chunkNav.navigateToArea(nArea, gui);
                if (nav.IsSuccess()) {
                    area = nArea.getRCArea();
                }
            }
        }

        if(area!=null) {
            NContext cnt = new NContext(gui);
            new FindAndEatItems(cnt, items, 8000, area, nArea).run(gui);
            boolean ok = NUtils.getEnergy()*10000 > 8000;
            gui.msg("Eater: " + (ok ? "done" : "energy < 80%"));
            return ok ? Results.SUCCESS() : Results.FAIL();
        } else {
            gui.msg("Eater: no area with 'eat' spec");
            return Results.FAIL();
        }
        if (items.isEmpty()) {
            return Results.ERROR("No allowed food items configured");
        }

        NContext cnt = new NContext(gui);
        Results res = new FindAndEatItems(cnt, items, 8000).run(gui);
        if (!res.IsSuccess()) {
            return res;
        }
        return NUtils.getEnergy() * 10000 > 8000 ? Results.SUCCESS() : Results.FAIL();
    }
}
