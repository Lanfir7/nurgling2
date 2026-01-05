package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Pair;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Build;
import nurgling.actions.CloseTargetWindow;
import nurgling.actions.OpenTargetContainer;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.actions.TakeItemsFromPile;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.navigation.ChunkNavManager;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Custom action to collect boards from specialized zone using routes.
 * This is used as specialWay in Build.Ingredient to enable route-based collection
 * when the ingredient zone is in a different dimension.
 */
public class CollectBoardsFromZoneAction implements Action {
    private final Pair<Coord2d, Coord2d> boardsRCArea;
    private final NAlias itemName;
    private final Build.Ingredient ingredient;
    private final Pair<Coord2d, Coord2d> buildAreaRCArea;
    private final long buildAreaGridId; // Grid ID saved before leaving build instance
    private final NContext context;

    public CollectBoardsFromZoneAction(Pair<Coord2d, Coord2d> boardsRCArea, NAlias itemName, 
                                       Build.Ingredient ingredient, Pair<Coord2d, Coord2d> buildAreaRCArea, 
                                       long buildAreaGridId, NContext context) {
        this.boardsRCArea = boardsRCArea;
        this.itemName = itemName;
        this.ingredient = ingredient;
        this.buildAreaRCArea = buildAreaRCArea;
        this.buildAreaGridId = buildAreaGridId;
        this.context = context;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // Navigate to boards area using routes
        NArea boardsArea = context.getSpecArea(Specialisation.SpecName.boardsForBuild);
        if (boardsArea == null) {
            return Results.ERROR("Zone with specialization 'Boards for build' not found!");
        }

        // Get actual boards area after navigation (may be in different dimension now)
        Pair<Coord2d, Coord2d> actualBoardsRCArea = boardsArea.getRCArea();
        if (actualBoardsRCArea == null) {
            return Results.ERROR("Zone with specialization 'Boards for build' has no valid area!");
        }

        // Check how many boards we already have and how many we need
        int boardsInInventory = NUtils.getGameUI().getInventory().getItems(itemName).size();
        int boardsNeeded = ingredient.count;
        int boardsToCollect = Math.max(0, boardsNeeded - boardsInInventory);
        
        // Collect boards from stockpiles until we have enough or inventory is full
        while (boardsToCollect > 0 && NUtils.getGameUI().getInventory().getNumberFreeCoord(ingredient.coord) > 0) {
            // Use actual boards area after navigation
            ArrayList<Gob> piles = Finder.findGobs(actualBoardsRCArea, new NAlias("stockpile"));
            if (piles.isEmpty()) {
                // No more piles found, check if we have enough boards
                int currentBoards = NUtils.getGameUI().getInventory().getItems(itemName).size();
                if (currentBoards < boardsNeeded) {
                    return Results.ERROR("Not enough boards in stockpiles! Need " + boardsNeeded + ", have " + currentBoards);
                }
                break;
            }
            piles.sort(NUtils.d_comp);
            Gob pile = piles.get(0);
            
            new PathFinder(pile).run(gui);
            new OpenTargetContainer("Stockpile", pile).run(gui);
            
            int freeSpace = NUtils.getGameUI().getInventory().getNumberFreeCoord(ingredient.coord);
            if (freeSpace > 0) {
                TakeItemsFromPile tifp = new TakeItemsFromPile(pile, NUtils.getGameUI().getStockpile(), 
                    Math.min(boardsToCollect, freeSpace));
                tifp.run(gui);
                int collected = tifp.getResult();
                boardsToCollect -= collected;
                // Don't modify ingredient.count here - Build.java will update it based on actual inventory
            } else {
                break;
            }
            
            new CloseTargetWindow(NUtils.getGameUI().getWindow("Stockpile")).run(gui);
        }

        // Return to build area after collecting (using ChunkNav for cross-instance navigation)
        if (buildAreaRCArea != null && buildAreaGridId != -1) {
            System.out.println("CollectBoardsFromZoneAction: Returning to build area, grid ID: " + buildAreaGridId);
            
            // Try ChunkNav first for cross-instance navigation (through doors/portals)
            boolean navigatedWithChunkNav = false;
            try {
                ChunkNavManager chunkNav = (gui.map instanceof NMapView) 
                    ? ((NMapView) gui.map).getChunkNavManager() : null;
                if (chunkNav != null && chunkNav.isInitialized()) {
                    // Create temporary area with SAVED grid ID for navigation target
                    NArea tempArea = new NArea("_temp_build_area");
                    // Use empty constructor to avoid grid lookup!
                    tempArea.space = new NArea.Space();
                    tempArea.space.space = new HashMap<>();
                    
                    // Add the saved grid ID to area's space mapping
                    // Use center of grid as target (50,50 in local coords)
                    tempArea.space.space.put(buildAreaGridId, new NArea.VArea(
                        new haven.Area(new Coord(45, 45), new Coord(55, 55))
                    ));
                    
                    System.out.println("CollectBoardsFromZoneAction: Navigating with ChunkNav to grid " + buildAreaGridId);
                    Results navResult = chunkNav.navigateToArea(tempArea, gui);
                    if (navResult.IsSuccess()) {
                        navigatedWithChunkNav = true;
                        System.out.println("CollectBoardsFromZoneAction: ChunkNav navigation successful");
                    } else {
                        System.out.println("CollectBoardsFromZoneAction: ChunkNav navigation failed");
                    }
                }
            } catch (Exception e) {
                System.err.println("CollectBoardsFromZoneAction: ChunkNav navigation failed: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Fallback to regular PathFinder if ChunkNav failed or unavailable
            if (!navigatedWithChunkNav) {
                System.out.println("CollectBoardsFromZoneAction: Fallback to PathFinder");
                Coord2d buildCenter = new Coord2d(
                    (buildAreaRCArea.a.x + buildAreaRCArea.b.x) / 2.0,
                    (buildAreaRCArea.a.y + buildAreaRCArea.b.y) / 2.0
                );
                try {
                    new PathFinder(buildCenter).run(gui);
                } catch (Exception e) {
                    gui.error("Can't return to build area: " + e.getMessage());
                }
            }
        } else if (buildAreaRCArea != null) {
            // No grid ID saved - try regular PathFinder
            System.out.println("CollectBoardsFromZoneAction: No grid ID saved, using PathFinder");
            Coord2d buildCenter = new Coord2d(
                (buildAreaRCArea.a.x + buildAreaRCArea.b.x) / 2.0,
                (buildAreaRCArea.a.y + buildAreaRCArea.b.y) / 2.0
            );
            try {
                new PathFinder(buildCenter).run(gui);
            } catch (Exception e) {
                gui.error("Can't return to build area: " + e.getMessage());
            }
        }

        return Results.SUCCESS();
    }
}

