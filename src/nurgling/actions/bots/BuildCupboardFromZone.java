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
import nurgling.routes.RouteGraph;
import nurgling.routes.RoutePoint;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

public class BuildCupboardFromZone implements Action {
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        try {
        Build.Command command = new Build.Command();
        command.name = "Cupboard";

        NUtils.getGameUI().msg("Please, select build area");
        SelectAreaWithLiveGhosts buildarea = new SelectAreaWithLiveGhosts(Resource.loadsimg("baubles/buildArea"), "Cupboard");
        buildarea.run(NUtils.getGameUI());

        // Save build area data BEFORE navigation (to avoid grid null issues after navigation)
        Pair<Coord2d, Coord2d> buildRCArea = buildarea.getRCArea();
        int rotationCount = buildarea.getRotationCount();

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

        // Find and save RoutePoint for build area BEFORE navigation to boards area
        // This is the starting point we need to return to after collecting boards
        RouteGraph graph = ((NMapView) NUtils.getGameUI().map).routeGraphManager.getGraph();
        RoutePoint buildAreaRoutePoint = null;
        // Try to find a temporary area for build zone to get its RoutePoint
        // Since build area is selected by user, we need to find nearest RoutePoint to build area center
        Coord2d buildCenter = new Coord2d(
            (buildRCArea.a.x + buildRCArea.b.x) / 2.0,
            (buildRCArea.a.y + buildRCArea.b.y) / 2.0
        );
        // Find nearest RoutePoint to build area center
        RoutePoint nearestToBuild = graph.findNearestPointToPlayer(NUtils.getGameUI());
        if (nearestToBuild != null) {
            // Get player's current grid and position to find starting RoutePoint
            Coord playerTile = NUtils.player().rc.floor(MCache.tilesz);
            MCache.Grid playerGrid = NUtils.getGameUI().map.glob.map.getgridt(playerTile);
            long playerGridId = playerGrid.id;
            Coord playerLocalCoord = playerTile.sub(playerGrid.ul);
            buildAreaRoutePoint = graph.findNearestPoint(playerGridId, playerLocalCoord);
        }

        // Get boards area from specialization using NContext to enable route navigation
        NContext context = new NContext(gui);
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
        Build.Ingredient boardsIngredient = new Build.Ingredient(new Coord(4,1), boardsRCArea, new NAlias("Board"), 8);
        CollectBoardsFromZoneAction collectBoardsAction = new CollectBoardsFromZoneAction(
            boardsRCArea, new NAlias("Board"), boardsIngredient, buildAreaRoutePoint, context);
        boardsIngredient.specialWay = collectBoardsAction;
        command.ingredients.add(boardsIngredient);
        
        new Build(command, buildRCArea, rotationCount, ghostPositions, ghostPreview).run(gui);
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

