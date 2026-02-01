package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Pair;
import haven.Resource;
import haven.WItem;
import nurgling.NGameUI;
import nurgling.NGob;
import nurgling.NGItem;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.tasks.WaitFreeHand;
import nurgling.tasks.WaitPile;
import nurgling.tasks.WaitPlob;
import nurgling.tools.Finder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static haven.Inventory.sqsz;
import static haven.OCache.posres;

/**
 * Fills the selected zone with stockpiles. The first slot defines the item TYPE (e.g. put one wood block there).
 * Uses ALL items of that type from the entire inventory to create stockpiles (one item per pile) until zone is full or items run out.
 */
public class CreateStockpilesFromFirstSlot implements Action {

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        NUtils.getGameUI().msg("Please, select output area");
        SelectArea outsa = new SelectArea(Resource.loadsimg("baubles/outputArea"));
        outsa.run(gui);
        Pair<Coord2d, Coord2d> out = outsa.getRCArea();

        ArrayList<WItem> allItems = gui.getInventory().getItems();
        if (allItems.isEmpty()) {
            return Results.ERROR("Inventory is empty.");
        }

        // First slot = minimum slot position (row then column)
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

        // Fill zone: use ALL items of this exact type from entire inventory, one item per stockpile
        while (true) {
            ArrayList<WItem> allInv = gui.getInventory().getItems();
            ArrayList<WItem> itemsOfType = new ArrayList<>();
            for (WItem w : allInv) {
                if (((NGItem) w.item).name().equals(itemName)) {
                    itemsOfType.add(w);
                }
            }
            if (itemsOfType.isEmpty()) {
                break;
            }

            if (!gui.hand.isEmpty()) {
                return Results.FAIL();
            }
            if (NUtils.takeItemToHand(itemsOfType.get(0)) == null) {
                return Results.FAIL();
            }

            NUtils.activateItem(out.a);
            NUtils.getUI().core.addTask(new WaitPlob());
            Coord2d pos = Finder.getFreePlace(out, NUtils.getGameUI().map.placing.get().ngob.hitBox);
            if (pos == null) {
                break; // Zone full
            }

            new PathFinder(NGob.getDummy(pos, 0, NUtils.getGameUI().map.placing.get().ngob.hitBox), true).run(gui);
            NUtils.addTask(new WaitStockpile(false));
            NUtils.getGameUI().map.wdgmsg("place", pos.floor(posres), 0, 1, 0);
            WaitPile wp = new WaitPile(pos);
            NUtils.addTask(wp);
            NUtils.addTask(new WaitStockpile(true));
            NUtils.addTask(new WaitFreeHand()); // Wait for hand to be free before next iteration
        }

        return Results.SUCCESS();
    }
}
