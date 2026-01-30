package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.MCache;
import haven.Pair;
import haven.Resource;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Build;
import nurgling.actions.Results;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.overlays.BuildGhostPreview;
import nurgling.overlays.NCustomBauble;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

public class BuildCupboardFromZone implements Action {
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        try {
        Build.Command command = new Build.Command();
        command.name = "Cupboard";

        NContext context = new NContext(gui);
        NUtils.getGameUI().msg("Please, select build area");
        SelectAreaWithLiveGhosts buildarea = new SelectAreaWithLiveGhosts(context, Resource.loadsimg("baubles/buildArea"), "Cupboard");
        buildarea.run(NUtils.getGameUI());

        // Save build area data BEFORE navigation (to avoid grid null issues after navigation)
        Pair<Coord2d, Coord2d> buildRCArea = buildarea.getRCArea();
        int rotationCount = buildarea.getRotationCount();
        
        // IMPORTANT: Save the grid ID of build area BEFORE leaving this instance
        // This is needed for ChunkNav to navigate back through portals
        // Use player's current grid ID which is more reliable than getgridt()
        long buildAreaGridId = -1;
        try {
            Gob playerGob = NUtils.player();
            if (playerGob != null && playerGob.ngob != null && playerGob.ngob.grid_id != 0) {
                buildAreaGridId = playerGob.ngob.grid_id;
                System.out.println("BuildCupboardFromZone: Saved build area grid ID: " + buildAreaGridId);
            }
        } catch (Exception e) {
            System.err.println("BuildCupboardFromZone: Could not get build area grid ID: " + e.getMessage());
        }

        // Get ghost positions from BuildGhostPreview if available (before navigation)
        ArrayList<Coord2d> ghostPositions = null;
        BuildGhostPreview ghostPreview = null;
        Gob player = NUtils.player();
        if (player != null) {
            ghostPreview = player.getattr(BuildGhostPreview.class);
            if (ghostPreview != null) {
                ghostPositions = new ArrayList<>(ghostPreview.getGhostPositions());
            }
        }

        // Get boards area from specialization using NContext to enable route navigation
        NArea boardsArea = context.getSpecArea(Specialisation.SpecName.boardsForBuild);
        if (boardsArea == null) {
            NUtils.getGameUI().error("Zone with specialization 'Boards for build' not found!");
            return Results.FAIL();
        }
        
        Pair<Coord2d, Coord2d> boardsRCArea = boardsArea.getRCArea();
        if (boardsRCArea == null) {
            NUtils.getGameUI().error("Zone with specialization 'Boards for build' has no valid area!");
            return Results.FAIL();
        }
        
        // Create ingredient with specialWay that uses routes for collection
        Build.Ingredient boardsIngredient = new Build.Ingredient(new Coord(4,1), boardsArea, new NAlias("Board"), 8);
        CollectBoardsFromZoneAction collectBoardsAction = new CollectBoardsFromZoneAction(
            boardsRCArea, new NAlias("Board"), boardsIngredient, buildRCArea, buildAreaGridId, context);
        boardsIngredient.specialWay = collectBoardsAction;
        command.ingredients.add(boardsIngredient);
        
        new Build(context, command, buildarea.ghostArea, rotationCount, ghostPositions, ghostPreview).run(gui);
        return Results.SUCCESS();
        } finally {
            // Always clean up ghost preview when bot finishes or is interrupted
            Gob player = NUtils.player();
            if (player != null) {
                BuildGhostPreview ghostPreview = player.getattr(BuildGhostPreview.class);
                if (ghostPreview != null) {
                    ghostPreview.dispose();
                    player.delattr(BuildGhostPreview.class);
                }

                // Remove custom bauble overlay
                Gob.Overlay baubleOverlay = player.findol(NCustomBauble.class);
                if (baubleOverlay != null) {
                    baubleOverlay.remove();
                }

                // Clean up area selection mode
                if (NUtils.getGameUI() != null && NUtils.getGameUI().map != null) {
                    ((NMapView) NUtils.getGameUI().map).isAreaSelectionMode.set(false);
                }
            }
        }
    }
}

