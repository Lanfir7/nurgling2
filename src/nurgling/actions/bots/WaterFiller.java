package nurgling.actions.bots;

import haven.Coord2d;
import haven.Gob;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.tasks.IsOverlay;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import static nurgling.tools.Finder.findLiftedbyPlayer;

/**
 * Fills empty barrels with water from a well.
 * Takes barrels from selected vehicle (Wagon, cart, etc.), fills at well, returns each to vehicle. Repeats until no barrels left.
 */
public class WaterFiller implements Action {

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        SelectGob vehicleSel = new SelectGob(haven.Resource.loadsimg("baubles/inputVeh"));
        NUtils.getGameUI().msg("Please select vehicle with barrels (Wagon, cart, etc.)");
        vehicleSel.run(gui);
        Gob vehicle = vehicleSel.result;
        if (vehicle == null)
            return Results.ERROR("Vehicle not selected");

        SelectGob wellSel = new SelectGob(haven.Resource.loadsimg("baubles/waterRefiller"));
        NUtils.getGameUI().msg("Please select well");
        wellSel.run(gui);
        Gob well = wellSel.result;
        if (well == null)
            return Results.ERROR("Well not selected");

        int maxSlots = vehicle.ngob.name != null && vehicle.ngob.name.contains("wagon") ? 20 :
                (vehicle.ngob.name != null && vehicle.ngob.name.contains("snekkja") ? 16 : 6);
        for (int slotIdx = 0; slotIdx < maxSlots; slotIdx++) {
            haven.Widget existingWnd = NUtils.getGameUI().getWindow("Wagon");
            if (existingWnd != null) {
                existingWnd.wdgmsg("close");
                Thread.sleep(500);
            }
            if (!new TakeFromVehicle(vehicle, slotIdx, false).run(gui).IsSuccess())
                continue;
            Gob barrel = findLiftedbyPlayer();
            if (barrel == null)
                break;
            if (barrel.ngob.name == null || !barrel.ngob.name.contains("barrel")) {
                new TransferToVehicle(barrel, vehicle).run(gui);
                continue;
            }
            if (NUtils.isOverlay(barrel, new NAlias("water"))) {
                new TransferToVehicle(barrel, vehicle).run(gui);
                continue;
            }

            new PathFinder(well).run(gui);
            NUtils.activateGob(well);
            IsOverlay waitWater = new IsOverlay(barrel, new NAlias("water"));
            NUtils.addTask(waitWater);
            if (!waitWater.getResult())
                return Results.ERROR("Barrel did not fill with water");

            new TransferToVehicle(barrel, vehicle).run(gui);

            Coord2d pos = NUtils.player().rc;
            new GoTo(pos.add(-2, -2)).run(gui);
        }
        return Results.SUCCESS();
    }
}
