package nurgling.actions;

import haven.Gob;
import haven.Pair;
import haven.Coord2d;
import haven.Resource;
import haven.WItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.actions.bots.SelectArea;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Ctrl+RMB smelter macro: select an area of Ore/Smith's smelters, skip those empty of ore,
 * fuel with coal (9 if all Well mined, else 12) from inventory or the fuel(coal) spec,
 * then light. Does not include {@code primsmelter}.
 */
public class FuelSmelters implements Action {

    static NAlias ores = new NAlias(new ArrayList<>(
            Arrays.asList("Cassiterite", "Lead Glance", "Wine Glance", "Chalcopyrite", "Malachite", "Peacock Ore", "Cinnabar", "Heavy Earth", "Iron Ochre",
                    "Bloodstone", "Black Ore", "Galena", "Silvershine", "Horn Silver", "Direvein", "Schrifterz", "Leaf Ore", "Meteorite", "Dross")));

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        gui.msg("Please, select smelter area");
        SelectArea sa = new SelectArea(Resource.loadsimg("baubles/inputArea"));
        sa.run(gui);
        Pair<Coord2d, Coord2d> area = sa.getRCArea();
        if (area == null)
            return Results.ERROR("No area selected");

        /* NAlias matches by substring and "gfx/terobjs/primsmelter" contains "smelter",
         * so without this exception every stack furnace would be treated as an Ore Smelter.
         * LightObject.getConfig documents the same trap. */
        NAlias oreSmelter = new NAlias(new ArrayList<>(Arrays.asList("gfx/terobjs/smelter")),
                new ArrayList<>(Arrays.asList("primsmelter")));

        ArrayList<Gob> smelters = Finder.findGobs(area, oreSmelter);
        if (smelters.isEmpty())
            return Results.ERROR("No smelters in selected area");

        ArrayList<Container> forFuel = new ArrayList<Container>();
        ArrayList<String> forLight = new ArrayList<String>();
        for (Gob sm : smelters) {
            PathFinder pf = new PathFinder(sm);
            pf.isHardMode = true;
            pf.run(gui);

            Container cont = new Container(sm, ((sm.ngob.getModelAttribute() & 128) == 128) ? "Smith's Smelter" : "Ore Smelter", null);
            cont.initattr(Container.FuelLvl.class);
            new OpenTargetContainer(cont).run(gui);

            NInventory inv = gui.getInventory(cont.cap);
            ArrayList<WItem> oreItems = (inv != null) ? inv.getItems(ores) : new ArrayList<WItem>();
            if (oreItems.isEmpty()) {
                new CloseTargetContainer(cont).run(gui);
                continue;
            }

            Container.FuelLvl fuelLvl = cont.getattr(Container.FuelLvl.class);
            fuelLvl.setMaxlvl(12);
            fuelLvl.setCredolvl(9);
            fuelLvl.setFueltype("coal");
            fuelLvl.update();
            int needed = fuelLvl.neededFuel();
            new CloseTargetContainer(cont).run(gui);
            forLight.add(cont.gobHash);
            if (needed > 0)
                forFuel.add(cont);
        }

        if (forLight.isEmpty())
            return Results.SUCCESS();

        if (!forFuel.isEmpty()) {
            Results fuelRes = new FuelToContainers(forFuel).run(gui);
            if (!fuelRes.IsSuccess())
                return fuelRes;
        }

        if (!new LightGob(forLight, 2).run(gui).IsSuccess())
            return Results.ERROR("I can't start a fire");
        return Results.SUCCESS();
    }
}
