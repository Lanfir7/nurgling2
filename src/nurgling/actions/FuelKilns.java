package nurgling.actions;

import haven.Gob;
import haven.Pair;
import haven.Coord2d;
import haven.Resource;
import haven.WItem;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.actions.bots.SelectArea;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.KilnFuelCatalog;
import nurgling.tools.NAlias;

import java.util.ArrayList;
import java.util.OptionalInt;

/**
 * Ctrl+RMB kiln macro: select an area of kilns, compute catalog fuel per kiln, refill
 * branches from the fuel(branch) spec if needed, then fill each kiln. Does not light.
 */
public class FuelKilns implements Action {

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        gui.msg("Please, select kiln area");
        SelectArea sa = new SelectArea(Resource.loadsimg("baubles/inputArea"));
        sa.run(gui);
        Pair<Coord2d, Coord2d> area = sa.getRCArea();
        if (area == null)
            return Results.ERROR("No area selected");

        ArrayList<Gob> kilns = Finder.findGobs(area, new NAlias("gfx/terobjs/kiln"));
        if (kilns.isEmpty())
            return Results.ERROR("No kilns in selected area");

        ArrayList<Container> forFuel = new ArrayList<Container>();
        for (Gob kiln : kilns) {
            PathFinder pf = new PathFinder(kiln);
            pf.isHardMode = true;
            pf.run(gui);

            Container cont = new Container(kiln, "Kiln", null);
            cont.initattr(Container.FuelLvl.class);
            new OpenTargetContainer(cont).run(gui);

            NInventory inv = gui.getInventory("Kiln");
            ArrayList<String> names = new ArrayList<String>();
            if (inv != null) {
                for (WItem item : inv.getItems()) {
                    String name = (item.item instanceof NGItem) ? ((NGItem) item.item).name() : null;
                    names.add(name);
                }
            }

            if (names.isEmpty()) {
                new CloseTargetContainer(cont).run(gui);
                continue;
            }

            for (String name : names) {
                if (!KilnFuelCatalog.fuelUnitsFor(name).isPresent()) {
                    new CloseTargetContainer(cont).run(gui);
                    return Results.ERROR("Unknown kiln item: " + name);
                }
            }

            OptionalInt maxNeeded = KilnFuelCatalog.maxFuelUnitsFor(names);
            int maxLvl = maxNeeded.getAsInt();
            Container.FuelLvl fuelLvl = cont.getattr(Container.FuelLvl.class);
            fuelLvl.setAbsMaxlvl(30);
            fuelLvl.setMaxlvl(maxLvl);
            fuelLvl.setFueltype("branch");
            fuelLvl.update();
            int needed = fuelLvl.neededFuel();
            new CloseTargetContainer(cont).run(gui);
            if (needed > 0)
                forFuel.add(cont);
        }

        if (forFuel.isEmpty())
            return Results.SUCCESS();

        return new FuelToContainers(forFuel).run(gui);
    }
}
