package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.Results;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.overlays.BuildGhostPreview;
import nurgling.overlays.NCustomBauble;
import nurgling.tasks.WaitCheckable;
import nurgling.tasks.WaitPlob;
import nurgling.widgets.bots.MultiAreaConfirm;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

public class SelectAreaWithLiveGhosts extends SelectArea {
    private String buildingName;
    private int rotationCount = 0;
    private NHitBox customHitBox = null;
    public NArea ghostArea;
    NContext context;

    public SelectAreaWithLiveGhosts(NContext context, BufferedImage image, String buildingName) {
        super(image);
        this.buildingName = buildingName;
        this.context = context;
    }

    public SelectAreaWithLiveGhosts(NContext context, BufferedImage image, String buildingName, NHitBox customHitBox) {
        super(image);
        this.buildingName = buildingName;
        this.customHitBox = customHitBox;
        this.context = context;
    }

    public int getRotationCount() {
        return rotationCount;
    }

    public static NHitBox hitBoxForBuilding(String buildingName, NHitBox customHitBox) {
        if (customHitBox != null) {
            return customHitBox;
        }
        BuildCatalog.BuildingDef def = BuildCatalog.get(buildingName);
        if (def == null) {
            return null;
        }
        return MixedGhostStore.lookupHitbox(def);
    }

    static String resourceNameForBuilding(String buildingName) {
        BuildCatalog.BuildingDef def = BuildCatalog.get(buildingName);
        return (def != null) ? def.resName : null;
    }

    static boolean canPreviewWithoutPlob(NHitBox hitBox, String resourceName) {
        return hitBox != null && resourceName != null && !resourceName.isEmpty();
    }

    static boolean matchesBuildButton(Supplier<String> nameSupplier, String buildingName) {
        if (nameSupplier == null || buildingName == null) {
            return false;
        }
        try {
            return buildingName.equals(nameSupplier.get());
        } catch (Loading e) {
            return false;
        }
    }

    public static void tryActivateBuildMenu(NGameUI gui, String buildingName) {
        if (gui == null || gui.menu == null || buildingName == null) {
            return;
        }
        for (MenuGrid.Pagina pag : gui.menu.paginae) {
            if (pag == null) {
                continue;
            }
            if (matchesBuildButton(() -> pag.button().name(), buildingName)) {
                try {
                    pag.button().use(new MenuGrid.Interaction(1, 0));
                } catch (Loading ignored) {
                }
                return;
            }
        }
    }

    private static void cancelPlacing(NGameUI gui) {
        if (gui == null || gui.map == null || gui.map.placing == null) {
            return;
        }
        try {
            Loader.Future<MapView.Plob> placing = gui.map.placing;
            if (placing.ready()) {
                placing.get().delattr(ResDrawable.class);
            }
            placing.cancel();
        } catch (Exception ignored) {
        }
        gui.map.placing = null;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        if (gui == null || gui.map == null) {
            return Results.FAIL();
        }
        NMapView mapView = (NMapView) gui.map;

        if (mapView.isAreaSelectionMode.get())
        {
            return Results.ERROR("Area selection already in progress");
        }

        Gob player = mapView.player();

        // NOTE: the selection bubble is NOT added here. NCustomBauble removes itself as
        // soon as isAreaSelectionMode is false (its tick() returns !flag), and the flag is
        // still false during setup below (it is only set true inside the selection loop).
        // Adding it now would let it self-remove before selection mode begins.

        NHitBox hitBox = hitBoxForBuilding(buildingName, customHitBox);
        String resName = resourceNameForBuilding(buildingName);
        Indir<Resource> resource = (resName != null) ? Resource.remote().load(resName) : null;
        Message sdt = Message.nil;

        if (!canPreviewWithoutPlob(hitBox, resName)) {
            tryActivateBuildMenu(gui, buildingName);
            if (gui.map.placing == null) {
                tryActivateBuildMenu(gui, buildingName);
            }
            gui.ui.core.addTask(WaitPlob.withSoftTimeout(false, 60, gui));
            Loader.Future<MapView.Plob> placing = gui.map.placing;
            if (placing != null && placing.ready()) {
                MapView.Plob plob = placing.get();
                if (hitBox == null && plob.ngob != null) {
                    hitBox = plob.ngob.hitBox;
                }
                ResDrawable rd = plob.getattr(ResDrawable.class);
                if (rd != null && rd.res != null) {
                    resource = rd.res;
                    if (rd.sdt != null) {
                        sdt = rd.sdt.clone();
                    }
                } else if (plob.ngob != null && plob.ngob.name != null) {
                    resource = Resource.remote().load(plob.ngob.name);
                }
            }
        }

        // Placement hologram steals map clicks; drop it before asking the user to select.
        cancelPlacing(gui);

        if (player != null)
        {
            BuildGhostPreview oldGhost = player.getattr(BuildGhostPreview.class);
            if (oldGhost != null)
            {
                oldGhost.dispose();
                player.delattr(BuildGhostPreview.class);
            }
        }

        // Multi-pass selection loop
        ArrayList<Gob> accumulatedGhosts = new ArrayList<>();
        NArea.Space combinedSpace = new NArea.Space();
        int areasSelected = 0;
        boolean userCancelled = false;

        while (true)
        {
            // Prepare selection state for this round
            mapView.areaSpace = null;
            mapView.currentSelectionCoords = null;
            mapView.rotationRequested = false;
            mapView.isAreaSelectionMode.set(true);

            // Show the "select build area" bubble above the player's head. Added here,
            // after the flag is true, so NCustomBauble.tick() does not immediately remove
            // it. Re-added every round; the previous round's bubble is auto-removed when the
            // flag drops to false at the end of that round's selection.
            if (image != null && player != null)
            {
                player.addcustomol(new NCustomBauble(player, image, spr, mapView.isAreaSelectionMode));
            }

            if (areasSelected == 0)
            {
                gui.msg("Please, select build area");
            } else
            {
                gui.msg("Please, select another build area (" + (areasSelected + 1) + ")");
            }

            nurgling.tasks.SelectAreaWithLiveGhosts sa =
                new nurgling.tasks.SelectAreaWithLiveGhosts(hitBox, resource, sdt, rotationCount, gui);
            gui.ui.core.addTask(sa);

            if (sa.getResult() == null)
            {
                userCancelled = (areasSelected == 0);
                break;
            }

            // Track rotation chosen this round so the next round starts there
            rotationCount = sa.getRotationCount();

            // Snapshot this round's ghosts and take ownership so they remain visible
            // when the next round's task creates a fresh preview.
            BuildGhostPreview roundPreview = player != null ? player.getattr(BuildGhostPreview.class) : null;
            if (roundPreview != null)
            {
                accumulatedGhosts.addAll(roundPreview.takeGhosts());
            }

            mergeSpace(combinedSpace, sa.getResult());
            areasSelected++;

            // Ask whether to add another area
            int totalPositions = accumulatedGhosts.size();
            MultiAreaConfirm confirm = new MultiAreaConfirm(buildingName, totalPositions, areasSelected);
            gui.ui.core.addTask(new WaitCheckable(
                NUtils.addCentered(gui, confirm)
            ));
            MultiAreaConfirm.State state = confirm.getState();
            confirm.destroy();

            if (state == MultiAreaConfirm.State.BUILD || state == MultiAreaConfirm.State.CANCELLED)
            {
                break;
            }
            // Otherwise loop for another area
        }

        // Tear down any leftover empty preview that the inner task left attached
        if (player != null)
        {
            BuildGhostPreview leftover = player.getattr(BuildGhostPreview.class);
            if (leftover != null)
            {
                player.delattr(BuildGhostPreview.class);
            }
        }

        mapView.isAreaSelectionMode.set(false);

        if (areasSelected == 0 || userCancelled || accumulatedGhosts.isEmpty())
        {
            return Results.FAIL();
        }

        // Attach a fresh preview that owns all accumulated ghosts so Build can call
        // removeGhost(pos) and dispose() against the same set the user saw.
        if (player != null)
        {
            BuildGhostPreview master = new BuildGhostPreview(player, null, hitBox, resource, rotationCount, sdt);
            master.addExistingGhosts(accumulatedGhosts);
            player.setattr(master);
        }

        String id = context.createAreaFromSpace(combinedSpace);
        ghostArea = context.goToAreaById(id);

        return Results.SUCCESS();
    }

    /**
     * Merge another Space into `target`. Per grid, take the bounding box of both areas.
     */
    private static void mergeSpace(NArea.Space target, NArea.Space addition)
    {
        if (addition == null || addition.space == null) return;
        for (Map.Entry<Long, NArea.VArea> e : addition.space.entrySet())
        {
            NArea.VArea existing = target.space.get(e.getKey());
            if (existing == null)
            {
                target.space.put(e.getKey(),
                    new NArea.VArea(new Area(e.getValue().area.ul, e.getValue().area.br)));
            } else
            {
                Coord ul = new Coord(
                    Math.min(existing.area.ul.x, e.getValue().area.ul.x),
                    Math.min(existing.area.ul.y, e.getValue().area.ul.y));
                Coord br = new Coord(
                    Math.max(existing.area.br.x, e.getValue().area.br.x),
                    Math.max(existing.area.br.y, e.getValue().area.br.y));
                target.space.put(e.getKey(), new NArea.VArea(new Area(ul, br)));
            }
        }
    }
}
