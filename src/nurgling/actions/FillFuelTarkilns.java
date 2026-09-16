package nurgling.actions;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.NArea;
import nurgling.tasks.HandIsFree;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.ArrayList;
import java.util.HashMap;

public class FillFuelTarkilns implements Action
{

    ArrayList<Gob> gobs;
    NArea fuel;
    NArea station;

    public FillFuelTarkilns(ArrayList<Gob> gobs, NArea fuel, NArea station) {
        this.gobs = gobs;
        this.fuel = fuel;
        this.station = station;
    }


    @Override
    public Results run(NGameUI gui) throws InterruptedException {

        if (!NUtils.navigateToArea(fuel))
            return Results.ERROR("Can't reach the tarkiln fuel area");
        ArrayList<Gob> piles = Finder.findGobs(fuel, new NAlias("stockpile"));
        if (piles.isEmpty()) {
            return Results.ERROR("NO FUEL IN AREA");
        }
        NAlias fuelname = null;
        Coord targetCoord = null;
        int num = 0;
        if (piles.get(0).ngob.name.contains("block")) {
            fuelname = new NAlias("block", "Block");
            targetCoord = new Coord(1, 2);
            num = 80;
        } else if (piles.get(0).ngob.name.contains("board")) {
            fuelname = new NAlias("board", "Board");
            targetCoord = new Coord(4, 1);
            num = 40;
        }
        if (fuelname == null) {
            return Results.ERROR("NO CORRECT FUEL IN AREA");
        }
        HashMap<Gob, Integer> needFuel = new HashMap<>();
        for (Gob gob : gobs) {
            needFuel.put(gob, num);
        }
        while (true) {
            int count = 0;
            int maxSize = NUtils.getGameUI().getInventory().getNumberFreeCoord(targetCoord);
            for (Integer val : needFuel.values()) {
                count += val;
                if (count >= maxSize) {
                    break;
                }
            }

            if (count == 0) {
                return Results.SUCCESS();
            }
            ArrayList<Gob> targetGobs = new ArrayList<>(needFuel.keySet());
            targetGobs.sort(NUtils.grid_comp);
            for (Gob gob : targetGobs) {
                while (needFuel.get(gob) != 0) {
                    if (NUtils.getGameUI().getInventory().getItems(fuelname).isEmpty()) {
                        int target_size = Math.min(maxSize, count);
                        while (target_size != 0 && NUtils.getGameUI().getInventory().getNumberFreeCoord(targetCoord) != 0) {
                            if (!NUtils.navigateToArea(fuel))
                                return Results.ERROR("Can't reach the tarkiln fuel area");
                            piles = Finder.findGobs(fuel, new NAlias("stockpile"));
                            if (piles.isEmpty()) {
                                if (gui.getInventory().getItems().isEmpty())
                                    return Results.ERROR("no fuel items");
                                else
                                    break;
                            }
                            piles.sort(NUtils.d_comp);

                            Gob pile = piles.get(0);
                            new PathFinder(pile).run(gui);
                            new OpenTargetContainer("Stockpile", pile).run(gui);
                            TakeItemsFromPile tifp;
                            (tifp = new TakeItemsFromPile(pile, gui.getStockpile(), Math.min(target_size, gui.getInventory().getFreeSpace()))).run(gui);
                            new CloseTargetWindow(NUtils.getGameUI().getWindow("Stockpile")).run(gui);
                            if (tifp.getResult() <= 0)
                                return Results.ERROR("Can't get fuel from the tarkiln stockpile");
                            target_size = target_size - tifp.getResult();
                        }
                    }
                    if (needFuel.get(gob) != 0) {
                        if (station != null && !NUtils.navigateToArea(station))
                            return Results.ERROR("Can't get back to the tarkilns");
                        Gob target = Finder.findGob(gob.id);
                        if (target == null)
                            return Results.ERROR("Lost track of a tarkiln while fuelling it");
                        new PathFinder(target).run(gui);

                        int fueled = 0;
                        while (fueled < needFuel.get(gob)) {
                            /* Using one fresh widget at a time preserves stack-fix semantics:
                             * consuming an item can destroy and recreate the remaining widgets. */
                            ArrayList<WItem> fuelItems = NUtils.getGameUI().getInventory().getItems(fuelname);
                            if (fuelItems.isEmpty())
                                break;
                            NUtils.takeItemToHand(fuelItems.get(0));
                            NUtils.activateItem(target);
                            NUtils.getUI().core.addTask(new HandIsFree(NUtils.getGameUI().getInventory()));
                            fueled++;
                        }
                        if (fueled == 0)
                            return Results.ERROR("Can't get any tarkiln fuel into the inventory");
                        needFuel.put(gob, needFuel.get(gob) - fueled);
                    }
                }
            }
        }
    }
}
