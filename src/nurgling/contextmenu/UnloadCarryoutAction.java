package nurgling.contextmenu;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

public class UnloadCarryoutAction implements GobContextAction {
    private static final NAlias VEHICLE = new NAlias("vehicle");

    static boolean matches(String name) {
        return NParser.checkName(name, VEHICLE);
    }

    @Override
    public boolean appliesTo(Gob gob) {
        return matches(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.unload_carryout");
    }

    @Override
    public Action create(Gob vehicle) {
        return gui -> {
            NArea dest = NContext.findSpec("carrierout");
            if (dest == null)
                return Results.ERROR("No CarrierOut zone found! Please create a global zone with 'carrierout' specialization.");

            while (new TakeFromVehicle(vehicle).run(gui).IsSuccess()) {
                Gob gob = Finder.findLiftedbyPlayer();
                new FindPlaceAndAction(gob, dest, true).run(gui);
                Coord2d shift = gob.rc.sub(NUtils.player().rc).norm().mul(2);
                new GoTo(NUtils.player().rc.sub(shift)).run(gui);
            }

            return Results.SUCCESS();
        };
    }
}
