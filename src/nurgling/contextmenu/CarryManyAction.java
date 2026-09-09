package nurgling.contextmenu;

import haven.*;
import nurgling.NGameUI;
import nurgling.NHitBox;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.GoTo;
import nurgling.actions.LiftObject;
import nurgling.actions.PathFinder;
import nurgling.actions.PlaceObject;
import nurgling.actions.Results;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.overlays.BuildGhostPreview;
import nurgling.overlays.NCustomBauble;
import nurgling.tasks.SelectAreaWithLiveGhosts;
import nurgling.tools.Finder;
import nurgling.tools.LiftableCatalog;
import nurgling.tools.NAlias;

import java.util.ArrayList;

/**
 * Ctrl+RMB macro: transfer many of the clicked gob type from a selected input zone
 * to a selected output zone. The object filter and placement preview both come
 * from the clicked gob, so no object-name dialog is needed.
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
        return gui -> transferMany(gui, clicked, clickedName, alias);
    }

    private static Results transferMany(NGameUI gui, Gob clicked, String clickedName, NAlias alias) throws InterruptedException {
        NContext context = new NContext(gui);

        String insaId = context.createArea("Please, select input area", Resource.loadsimg("baubles/inputArea"));
        NArea inarea = context.goToAreaById(insaId);
        ArrayList<Gob> sourceItems = findClickedType(inarea, clickedName, alias);
        if (sourceItems.isEmpty()) {
            gui.msg("No " + clickedName + " found in the input area");
            return Results.SUCCESS();
        }

        PlacementSelection selection = selectOutputArea(gui, context, sourceItems.size(), clicked, clickedName);
        if (selection == null || selection.positions.isEmpty()) {
            return Results.FAIL();
        }
        NArea outarea = selection.area;

        ArrayList<Gob> items;
        int slot = 0;
        try {
            while (!(items = findClickedType(inarea, clickedName, alias)).isEmpty()) {
                ArrayList<Gob> availableItems = new ArrayList<>();
                for (Gob currGob : items) {
                    if (PathFinder.isAvailable(currGob))
                        availableItems.add(currGob);
                }
                if (availableItems.isEmpty()) {
                    gui.msg("Can't reach any " + clickedName + " in current area, skipping...");
                    break;
                }
                if (slot >= selection.positions.size()) {
                    gui.msg("No more previewed placement slots");
                    break;
                }

                availableItems.sort(NUtils.d_comp);
                Gob item = availableItems.get(0);
                Coord2d position = selection.positions.get(slot++);

                new LiftObject(item).run(gui);
                selection.preview.removeGhost(position);
                Results placed = new PlaceObject(null, position, selection.angle).run(gui);
                if (!placed.IsSuccess())
                    return placed;

                Coord2d shift = item.rc.sub(NUtils.player().rc).norm().mul(2);
                new GoTo(NUtils.player().rc.sub(shift)).run(gui);
                NUtils.navigateToArea(inarea);
            }
        } finally {
            selection.preview.dispose();
            Gob player = gui.map.player();
            if (player != null && player.getattr(BuildGhostPreview.class) == selection.preview)
                player.delattr(BuildGhostPreview.class);
        }

        return Results.SUCCESS();
    }

    private static PlacementSelection selectOutputArea(NGameUI gui, NContext context, int count,
                                                        Gob model, String resourceName) throws InterruptedException {
        NMapView map = (NMapView) gui.map;
        Gob player = map.player();
        NHitBox hitBox = null;
        Indir<Resource> resource = null;
        Message spriteData = Message.nil;

        if (model != null) {
            hitBox = model.ngob.hitBox;
            ResDrawable drawable = model.getattr(ResDrawable.class);
            if (drawable != null) {
                resource = drawable.res;
                if (drawable.sdt != null)
                    spriteData = drawable.sdt.clone();
            }
        }
        if (hitBox == null)
            hitBox = NHitBox.findCustom(resourceName);
        if (resource == null)
            resource = Resource.remote().load(resourceName);

        if (player != null) {
            BuildGhostPreview old = player.getattr(BuildGhostPreview.class);
            if (old != null) {
                old.dispose();
                player.delattr(BuildGhostPreview.class);
            }
        }

        map.areaSpace = null;
        map.currentSelectionCoords = null;
        map.currentSelectionDrag = null;
        map.rotationRequested = false;
        map.isAreaSelectionMode.set(true);
        if (player != null)
            player.addcustomol(new NCustomBauble(player, Resource.loadsimg("baubles/outputArea"), null,
                    map.isAreaSelectionMode));
        gui.msg("Please, select output area (R: rotate, C: center)");

        SelectAreaWithLiveGhosts task = new SelectAreaWithLiveGhosts(
                hitBox, resource, spriteData, 0, gui, count);
        boolean keepPreview = false;
        try {
            gui.ui.core.addTask(task);
            if (task.getResult() == null)
                return null;

            BuildGhostPreview preview = player != null ? player.getattr(BuildGhostPreview.class) : null;
            if (preview == null)
                return null;
            ArrayList<Coord2d> positions = preview.getGhostPositions();
            if (positions.isEmpty()) {
                gui.error("No free placement slots in the output area");
                return null;
            }

            String outsaId = context.createAreaFromSpace(task.getResult());
            NArea outarea = context.goToAreaById(outsaId);
            keepPreview = true;
            return new PlacementSelection(outarea, positions, preview,
                    placementAngle(task.getRotationCount()));
        } finally {
            if (!keepPreview) {
                BuildGhostPreview abandoned = player != null ? player.getattr(BuildGhostPreview.class) : null;
                if (abandoned != null) {
                    abandoned.dispose();
                    player.delattr(BuildGhostPreview.class);
                }
                map.cancelAreaSelection();
            }
        }
    }

    static double placementAngle(int rotationCount) {
        int normalized = ((rotationCount % 4) + 4) % 4;
        return normalized * Math.PI / 2.0;
    }

    private static final class PlacementSelection {
        final NArea area;
        final ArrayList<Coord2d> positions;
        final BuildGhostPreview preview;
        final double angle;

        PlacementSelection(NArea area, ArrayList<Coord2d> positions,
                           BuildGhostPreview preview, double angle) {
            this.area = area;
            this.positions = positions;
            this.preview = preview;
            this.angle = angle;
        }
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
