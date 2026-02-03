package nurgling.actions.bots;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects tar from tar kilns into barrels.
 * Lifts a barrel from zone with barrel(Tar) specialization, goes to zone with tarkiln specialization,
 * right-clicks each tar kiln that has tar to collect tar into the lifted barrel,
 * then returns and places the barrel back. Repeats until no barrels or no tar kilns with tar.
 * Uses same icon as Tarkiln Refiller (tarkiln).
 * <p>
 * State is read from gob.ngob.getModelAttribute() (debug "marker"), like drying frames / trees with cones:
 * empty (no tar) = 22, has tar (e.g. full 54, possibly still with wood) = other values.
 */
public class CollectTarFromKilns implements Action {

    /** Empty tar kiln (no tar): marker 22. Has tar = e.g. 54 (with wood still) or other non-empty state. */
    private static final int TARKILN_EMPTY_MARKER = 22;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        NArea.Specialisation barrelTarSpec = new NArea.Specialisation(Specialisation.SpecName.barrel.toString(), "Tar");
        NArea.Specialisation tarkilnSpec = new NArea.Specialisation(Specialisation.SpecName.tarkiln.toString());

        NArea barrelArea = NContext.findSpec(barrelTarSpec);
        if (barrelArea == null) {
            barrelArea = NContext.findSpecGlobal(barrelTarSpec);
        }
        if (barrelArea == null) {
            return Results.ERROR("No zone with barrel(Tar) specialization found. Add an area with Barrel specialization and Tar sub-specialization.");
        }

        NArea tarkilnArea = NContext.findSpec(tarkilnSpec);
        if (tarkilnArea == null) {
            tarkilnArea = NContext.findSpecGlobal(tarkilnSpec);
        }
        if (tarkilnArea == null) {
            return Results.ERROR("No zone with Tarkilns specialization found.");
        }

        ArrayList<Gob> barrels = Finder.findGobs(barrelArea, new NAlias("barrel"), gui);
        ArrayList<Gob> availableBarrels = new ArrayList<>();
        for (Gob b : barrels) {
            if (PathFinder.isAvailable(b)) {
                availableBarrels.add(b);
            }
        }
        if (availableBarrels.isEmpty()) {
            return Results.ERROR("No barrels in Tar barrel zone or none reachable.");
        }
        availableBarrels.sort(NUtils.d_comp(gui));
        Gob barrel = availableBarrels.get(0);

        new LiftObject(barrel).run(gui);
        if (Finder.findLiftedbyPlayer(gui) == null) {
            return Results.ERROR("Failed to lift barrel.");
        }

        if (!NUtils.navigateToArea(tarkilnArea, gui)) {
            new FindPlaceAndAction(null, barrelArea).run(gui);
            return Results.ERROR("Cannot navigate to Tarkilns zone.");
        }

        ArrayList<Gob> allKilns = Finder.findGobs(tarkilnArea, new NAlias("gfx/terobjs/tarkiln"), gui);
        List<Gob> kilnsWithTar = allKilns.stream()
                .filter(g -> g.ngob.getModelAttribute() != -1 && g.ngob.getModelAttribute() != TARKILN_EMPTY_MARKER)
                .collect(Collectors.toList());
        kilnsWithTar.sort(NUtils.d_comp(gui));

        for (Gob kiln : kilnsWithTar) {
            if (!PathFinder.isAvailable(kiln)) continue;
            new PathFinder(kiln).run(gui);
            new CollectFromGob(kiln, "Collect tar", "gfx/borka/bushpickan", new Coord(1, 1), null, true).run(gui);
        }

        if (!NUtils.navigateToArea(barrelArea, gui)) {
            return Results.ERROR("Cannot navigate back to Tar barrel zone.");
        }
        new FindPlaceAndAction(null, barrelArea).run(gui);

        Gob pl = NUtils.player(gui);
        if (pl != null) {
            Coord2d shift = barrel.rc.sub(pl.rc).norm().mul(2);
            new GoTo(pl.rc.sub(shift)).run(gui);
        }

        return Results.SUCCESS();
    }
}
