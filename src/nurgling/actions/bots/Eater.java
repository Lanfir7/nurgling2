package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.widgets.FoodContainer;
import nurgling.widgets.Specialisation;

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

        NArea nArea = NContext.findSpec(Specialisation.SpecName.eat.toString());
        if (nArea == null)
            nArea = NContext.findSpecGlobal(Specialisation.SpecName.eat.toString());
        if (nArea == null) {
            gui.msg("Eater: no area with 'eat' spec");
            return Results.FAIL();
        }
        if (!NUtils.navigateToArea(nArea, true)) {
            gui.msg("Eater: cannot reach eat area");
            return Results.FAIL();
        }
        Pair<Coord2d, Coord2d> area = nArea.getRCArea();
        if (area == null) {
            gui.msg("Eater: eat area not loaded");
            return Results.FAIL();
        }

        NContext cnt = new NContext(gui);
        new FindAndEatItems(cnt, items, 8000, area, nArea).run(gui);
        boolean ok = NUtils.getEnergy() * 10000 > 8000;
        gui.msg("Eater: " + (ok ? "done" : "energy < 80%"));
        return ok ? Results.SUCCESS() : Results.FAIL();
    }
}
