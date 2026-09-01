package nurgling.contextmenu;

import haven.Coord2d;
import haven.Gob;
import haven.Resource;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.FindPlaceAndAction;
import nurgling.actions.GoTo;
import nurgling.actions.LiftObject;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tools.Finder;
import nurgling.tools.LiftableCatalog;
import nurgling.tools.NAlias;

import java.util.ArrayList;

/**
 * Ctrl+RMB macro: transfer many of the clicked gob type from a selected input zone
 * to a selected output zone. Same loop as {@code TransferLiftable} with
 * {@code requireGlobalZones=false}, but the object filter comes from the click
 * (no object-name dialog).
 */
public class CarryManyAction implements GobContextAction {

    @Override
    public boolean appliesTo(Gob gob) {
        return gob != null && gob.ngob != null && LiftableCatalog.isLiftable(gob.ngob.name);
    }

    @Override
    public String label() {
        return nurgling.i18n.L10n.get("context.carry_many");
    }

    @Override
    public Action create(Gob clicked) {
        String clickedName = clicked.ngob.name;
        NAlias alias = LiftableCatalog.objectFilter(clickedName);
        return gui -> transferMany(gui, clickedName, alias);
    }

    private static Results transferMany(NGameUI gui, String clickedName, NAlias alias) throws InterruptedException {
        NContext context = new NContext(gui);

        String insaId = context.createArea("Please, select input area", Resource.loadsimg("baubles/inputArea"));
        NArea inarea = context.goToAreaById(insaId);
        String outsaId = context.createArea("Please, select output area", Resource.loadsimg("baubles/outputArea"));
        NArea outarea = context.goToAreaById(outsaId);

        ArrayList<Gob> items;
        while (!(items = findClickedType(inarea, clickedName, alias)).isEmpty()) {
            ArrayList<Gob> availableItems = new ArrayList<>();
            for (Gob currGob : items) {
                if (PathFinder.isAvailable(currGob))
                    availableItems.add(currGob);
            }
            if (availableItems.isEmpty()) {
                NUtils.getGameUI().msg("Can't reach any " + clickedName + " in current area, skipping...");
                break;
            }

            availableItems.sort(NUtils.d_comp);
            Gob item = availableItems.get(0);

            new LiftObject(item).run(gui);
            new FindPlaceAndAction(null, outarea).run(gui);

            Coord2d shift = item.rc.sub(NUtils.player().rc).norm().mul(2);
            new GoTo(NUtils.player().rc.sub(shift)).run(gui);
            NUtils.navigateToArea(inarea);
        }

        return Results.SUCCESS();
    }

    static ArrayList<Gob> findClickedType(NArea inarea, String clickedName, NAlias alias) throws InterruptedException {
        ArrayList<Gob> found = Finder.findGobs(inarea, alias);
        ArrayList<Gob> exact = new ArrayList<>();
        for (Gob gob : found) {
            if (gob.ngob != null && LiftableCatalog.isExactResource(gob.ngob.name, clickedName))
                exact.add(gob);
        }
        return exact;
    }
}
