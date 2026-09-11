package nurgling.actions;

import haven.Coord2d;
import haven.Gob;
import haven.Pair;
import haven.Resource;
import haven.WItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.bots.SelectArea;
import nurgling.tasks.WaitFreeHand;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.ArrayList;

/** Fuels each selected oven with four inventory branches, then lights the batch. */
public class FuelOvens implements Action {
    private static final String OVEN_RESOURCE = "gfx/terobjs/oven";
    private static final String BRANCH = "Branch";
    private static final int BRANCHES_PER_OVEN = 4;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        gui.msg("Please, select oven area");
        SelectArea selector = new SelectArea(Resource.loadsimg("baubles/inputArea"));
        selector.run(gui);
        Pair<Coord2d, Coord2d> area = selector.getRCArea();
        if (area == null)
            return Results.ERROR("No area selected");

        ArrayList<Gob> ovens = findOvens(area);
        if (ovens.isEmpty())
            return Results.ERROR("No ovens in selected area");
        if (gui.getInventory() == null)
            return Results.ERROR("Player inventory is unavailable");
        if (gui.getInventory().getItems(BRANCH).size() < ovens.size() * BRANCHES_PER_OVEN)
            return Results.ERROR("Not enough branches in inventory to fuel all ovens");

        ArrayList<String> toLight = new ArrayList<>();
        for (Gob oven : ovens) {
            if (oven.ngob.hash == null)
                return Results.ERROR("Cannot identify oven to light");
            Results fueled = fuelOven(gui, oven);
            if (!fueled.IsSuccess())
                return fueled;
            toLight.add(oven.ngob.hash);
        }

        if (!new LightGob(toLight, 4).run(gui).IsSuccess())
            return Results.ERROR("I can't start a fire");
        return Results.SUCCESS();
    }

    private ArrayList<Gob> findOvens(Pair<Coord2d, Coord2d> area) throws InterruptedException {
        ArrayList<Gob> ovens = new ArrayList<>();
        for (Gob gob : Finder.findGobs(area, new NAlias(OVEN_RESOURCE))) {
            if (gob != null && gob.ngob != null && OVEN_RESOURCE.equals(gob.ngob.name))
                ovens.add(gob);
        }
        return ovens;
    }

    private Results fuelOven(NGameUI gui, Gob oven) throws InterruptedException {
        PathFinder pathFinder = new PathFinder(oven);
        pathFinder.isHardMode = true;
        if (!pathFinder.run(gui).IsSuccess())
            return Results.ERROR("Cannot reach oven");

        for (int branch = 0; branch < BRANCHES_PER_OVEN; branch++) {
            ArrayList<WItem> branches = gui.getInventory().getItems(BRANCH);
            if (branches.isEmpty())
                return Results.ERROR("Not enough branches in inventory to fuel all ovens");
            if (NUtils.takeItemToHand(branches.get(0)) == null)
                return Results.ERROR("Cannot take branch from inventory");
            NUtils.activateItem(oven);
            NUtils.getUI().core.addTask(new WaitFreeHand());
        }
        return Results.SUCCESS();
    }
}
