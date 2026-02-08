package nurgling.actions.bots;

import haven.*;
import nurgling.NGameUI;
import nurgling.NGItem;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitTargetSize;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static haven.Inventory.sqsz;

/**
 * Creates stockpiles based on the item in the first inventory slot and fills them completely.
 * Uses SHIFT-transfer (xfer2 with MOD_SHIFT) to move all matching items at once
 * instead of transferring one by one.
 */
public class CreateFullStockpilesFromFirstSlot implements Action {

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        NUtils.getGameUI().msg("Please, select output area");
        SelectArea outsa = new SelectArea(Resource.loadsimg("baubles/outputArea"));
        outsa.run(gui);
        Pair<Coord2d, Coord2d> out = outsa.getRCArea();

        // Determine item type from first inventory slot
        ArrayList<WItem> allItems = gui.getInventory().getItems();
        if (allItems.isEmpty()) {
            return Results.ERROR("Inventory is empty.");
        }

        Coord firstSlot = allItems.stream()
                .map(w -> w.c.sub(1, 1).div(sqsz))
                .min(Comparator.comparingInt((Coord c) -> c.y).thenComparingInt(c -> c.x))
                .orElse(null);
        if (firstSlot == null) {
            return Results.ERROR("No items in inventory.");
        }

        List<WItem> firstSlotItems = allItems.stream()
                .filter(w -> w.c.sub(1, 1).div(sqsz).equals(firstSlot))
                .collect(Collectors.toList());
        if (firstSlotItems.isEmpty()) {
            return Results.ERROR("No item in first slot.");
        }

        String itemName = ((NGItem) firstSlotItems.get(0).item).name();
        NAlias pileName = getStockpileName(new NAlias(itemName));

        // Phase 1: Fill existing non-full stockpiles in the zone
        for (Gob gob : Finder.findGobs(out, pileName)) {
            if (getMatchingItems(gui, itemName).isEmpty()) break;
            if (gob.ngob.getModelAttribute() == 31 || !PathFinder.isAvailable(gob)) continue;

            // If holding item in hand, put it into the pile
            if (NUtils.getGameUI().vhand != null) {
                NUtils.activateItem(gob, false);
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        return NUtils.getGameUI().vhand == null;
                    }
                });
            }

            PathFinder pf = new PathFinder(gob);
            pf.isHardMode = true;
            pf.run(gui);

            new OpenTargetContainer("Stockpile", gob).run(gui);
            bulkPut(gui, itemName);
            new CloseTargetContainer("Stockpile").run(gui);
        }

        // Phase 2: Create new stockpiles and fill each one via SHIFT-transfer
        while (!getMatchingItems(gui, itemName).isEmpty()) {
            PileMaker pm = new PileMaker(out, itemName, pileName, 0);
            if (!pm.run(gui).IsSuccess()) {
                break;
            }

            // After PileMaker, stockpile window is already open
            // Fill it with bulk transfer
            bulkPut(gui, itemName);

            // Close stockpile before next iteration (PileMaker waits for WaitStockpile(false))
            new CloseTargetContainer("Stockpile").run(gui);
        }

        return Results.SUCCESS();
    }

    /**
     * Bulk-transfers matching items from inventory to the open stockpile.
     * Uses put() which sends a batch of xfer2 messages for fast transfer
     * instead of the slow one-by-one take-to-hand/drop approach.
     */
    private void bulkPut(NGameUI gui, String itemName) throws InterruptedException {
        NUtils.addTask(new WaitStockpile(true));

        int freeSpace = gui.getStockpile().getFreeSpace();
        if (freeSpace <= 0) return;

        int matchingCount = getMatchingItems(gui, itemName).size();
        int willTransfer = Math.min(matchingCount, freeSpace);
        if (willTransfer <= 0) return;

        int fullSize = gui.getInventory().getItems().size();

        // Bulk transfer: put() sends willTransfer xfer2 messages rapidly
        gui.getStockpile().put(willTransfer);

        // Wait for inventory to decrease by the expected amount
        NUtils.getUI().core.addTask(new WaitTargetSize(
                NUtils.getGameUI().getInventory(), fullSize - willTransfer));
    }

    private ArrayList<WItem> getMatchingItems(NGameUI gui, String itemName) throws InterruptedException {
        ArrayList<WItem> allItems = gui.getInventory().getItems();
        ArrayList<WItem> matches = new ArrayList<>();
        for (WItem w : allItems) {
            if (((NGItem) w.item).name().equals(itemName)) {
                matches.add(w);
            }
        }
        return matches;
    }

    /**
     * Determines stockpile resource name for finding existing piles in the zone.
     */
    private NAlias getStockpileName(NAlias items) {
        if (NParser.checkName(items.getDefault(), new NAlias("Soil"))) {
            return new NAlias("gfx/terobjs/stockpile-soil");
        } else if (NParser.checkName(items.getDefault(), new NAlias("board"))) {
            return new NAlias("gfx/terobjs/stockpile-board");
        } else if (NParser.checkName(items.getDefault(), new NAlias("Pumpkin Flesh"))) {
            return new NAlias("gfx/terobjs/stockpile-trash");
        } else if (NParser.checkName(items.getDefault(), new NAlias("pumpkin"))) {
            return new NAlias("gfx/terobjs/stockpile-pumpkin");
        } else if (NParser.checkName(items.getDefault(), new NAlias("metal"))) {
            return new NAlias("gfx/terobjs/stockpile-metal");
        } else if (NParser.checkName(items.getDefault(), new NAlias("brick"))) {
            return new NAlias("gfx/terobjs/stockpile-brick");
        } else if (NParser.checkName(items.getDefault(), new NAlias("fresh leaf of pipeweed"))) {
            return new NAlias("gfx/terobjs/stockpile-pipeleaves");
        } else if (NParser.checkName(items.getDefault(), new NAlias("Hemp Cloth"))) {
            return new NAlias("gfx/terobjs/stockpile-cloth");
        } else if (NParser.checkName(items.getDefault(), new NAlias("Linen Cloth"))) {
            return new NAlias("gfx/terobjs/stockpile-cloth");
        } else if (NParser.checkName(items.getDefault(), new NAlias("coal"))) {
            return new NAlias("gfx/terobjs/stockpile-coal");
        } else if (NParser.checkName(items.getDefault(), new NAlias("onion"))) {
            return new NAlias("gfx/terobjs/stockpile-onion");
        } else if (NParser.checkName(items.getDefault(), new NAlias("bone"))) {
            return new NAlias("gfx/terobjs/stockpile-bone");
        } else {
            return new NAlias("stockpile");
        }
    }
}
