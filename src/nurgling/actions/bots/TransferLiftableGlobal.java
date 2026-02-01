package nurgling.actions.bots;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.conf.NCarrierProp;
import nurgling.tasks.WaitCheckable;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

/**
 * Transfers liftable objects using global zones with chunk navigation.
 * Uses global CarrierOut zone for output.
 * For input: uses global CarrierIn zone if exists, otherwise prompts user to select.
 */
public class TransferLiftableGlobal implements Action {

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        nurgling.widgets.bots.Carrier w = null;
        NCarrierProp prop = null;
        try {
            NUtils.getUI().core.addTask(new WaitCheckable(NUtils.getGameUI().add((w = new nurgling.widgets.bots.Carrier()), UI.scale(200, 200))));
            prop = w.prop;
        } catch (InterruptedException e) {
            throw e;
        } finally {
            if (w != null)
                w.destroy();
        }
        if (prop == null) {
            return Results.ERROR("No config");
        }

        // Create context for global transfer
        NContext context = new NContext(gui);

        // Find CarrierOut area for output: use selected zone from settings, else global
        NArea carrierOutArea = null;
        if (prop.targetZoneId != null) {
            carrierOutArea = NUtils.getArea(prop.targetZoneId);
        }
        if (carrierOutArea == null) {
            NArea.Specialisation carrierOutSpec = new NArea.Specialisation(Specialisation.SpecName.carrierout.toString());
            carrierOutArea = NContext.findSpecGlobal(carrierOutSpec);
        }
        if (carrierOutArea == null) {
            return Results.ERROR("No CarrierOut zone found! Please create a global zone with 'carrierout' specialization or select one in macro settings.");
        }

        // Find CarrierIn area for input - try global first, fallback to selection
        // Note: We need a specialization for carrier input, using sorting as fallback
        NArea.Specialisation carrierInSpec = new NArea.Specialisation(Specialisation.SpecName.sorting.toString());
        NArea inarea = NContext.findSpecGlobal(carrierInSpec);

        if (inarea == null) {
            // Fallback: prompt user to select input area
            String insaId = context.createArea("Please, select input area", Resource.loadsimg("baubles/inputArea"));
            inarea = context.getAreaById(insaId);
        } else {
            // Navigate to the global input area
            NUtils.navigateToArea(inarea);
        }

        ArrayList<Gob> items;
        while (!(items = Finder.findGobs(inarea, new NAlias(prop.object))).isEmpty()) {
            ArrayList<Gob> availableItems = new ArrayList<>();
            for (Gob currGob : items) {
                if (PathFinder.isAvailable(currGob))
                    availableItems.add(currGob);
            }
            if (availableItems.isEmpty()) {
                NUtils.getGameUI().msg("Can't reach any " + prop.object + " in current area, skipping...");
                break;
            }

            availableItems.sort(NUtils.d_comp);
            Gob item = availableItems.get(0);

            // Lift the item
            new LiftObject(item).run(gui);

            // Navigate to output area - check if navigation succeeded
            if (!NUtils.navigateToArea(carrierOutArea)) {
                String zoneName = (carrierOutArea.name != null) ? carrierOutArea.name : ("#" + carrierOutArea.id);
                return Results.ERROR("Cannot navigate to output zone '" + zoneName + "'. " +
                    "The zone may be too far away. Try walking closer to it first.");
            }
            
            // Move to output area and place the item
            // Use NArea constructor so FindPlaceAndAction can navigate to zone if needed
            new FindPlaceAndAction(null, carrierOutArea).run(gui);

            // Move away from the placed item
            Coord2d shift = item.rc.sub(NUtils.player().rc).norm().mul(2);
            new GoTo(NUtils.player().rc.sub(shift)).run(gui);
            
            // Navigate back to input area - check if navigation succeeded
            if (!NUtils.navigateToArea(inarea)) {
                String zoneName = (inarea.name != null) ? inarea.name : ("#" + inarea.id);
                return Results.ERROR("Cannot navigate back to input zone '" + zoneName + "'. " +
                    "The zone may be too far away. Try walking closer to it first.");
            }
        }

        return Results.SUCCESS();
    }
}
