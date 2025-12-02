package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Pair;
import nurgling.NGameUI;
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
import nurgling.routes.RoutePoint;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

/**
 * Custom action to collect boards from specialized zone using routes.
 * This is used as specialWay in Build.Ingredient to enable route-based collection
 * when the ingredient zone is in a different dimension.
 */
public class CollectBoardsFromZoneAction implements Action {
    private final Pair<Coord2d, Coord2d> boardsRCArea;
    private final NAlias itemName;
    private final Build.Ingredient ingredient;
    private final RoutePoint buildAreaRoutePoint;
    private final NContext context;

    public CollectBoardsFromZoneAction(Pair<Coord2d, Coord2d> boardsRCArea, NAlias itemName, 
                                       Build.Ingredient ingredient, RoutePoint buildAreaRoutePoint, NContext context) {
        this.boardsRCArea = boardsRCArea;
        this.itemName = itemName;
        this.ingredient = ingredient;
        this.buildAreaRoutePoint = buildAreaRoutePoint;
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

        // Return to build area RoutePoint after collecting
        if (buildAreaRoutePoint != null) {
            new RoutePointNavigator(buildAreaRoutePoint).run(gui);
        }

        return Results.SUCCESS();
    }
}

