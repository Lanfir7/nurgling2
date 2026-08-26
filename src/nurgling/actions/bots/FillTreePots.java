package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.conf.NFillTreePotsProp;
import nurgling.tasks.*;
import nurgling.tools.*;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

/**
 * Fills Treeplanter's Pots with soil (4 per pot) and water (1L per pot).
 * Shows widget to select mulch zone (soilForTrees specialization).
 */
public class FillTreePots implements Action {

    private static final NAlias POT = new NAlias("Treeplanter's Pot");
    private static final NAlias SOIL = new NAlias("Soil", "Mulch");

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null || gui.ui == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
            return Results.FAIL();
        }
        nurgling.widgets.bots.FillTreePotsWidget w = null;
        NFillTreePotsProp prop = null;
        try {
            gui.ui.core.addTask(new WaitCheckable(
                NUtils.addCentered(gui, (w = new nurgling.widgets.bots.FillTreePotsWidget()))
            ));
            prop = w.prop;
        } catch (InterruptedException e) {
            throw e;
        } finally {
            if (w != null) w.destroy();
        }

        if (prop == null || prop.mulchZoneId == null) {
            return Results.FAIL();
        }

        NArea mulchArea = gui.map.glob.map.areas.get(prop.mulchZoneId);
        if (mulchArea == null) {
            return Results.ERROR("Selected mulch zone not found.");
        }

        // Count pots needing soil or water
        ArrayList<WItem> pots = gui.getInventory().getItems(POT);
        ArrayList<WItem> potsToFill = filterPotsNeedingFill(pots);
        if (potsToFill.isEmpty()) {
            gui.msg("No Treeplanter's Pots need soil or water.");
            return Results.SUCCESS();
        }

        gui.msg("Found " + potsToFill.size() + " pots to fill");
        NContext context = new NContext(gui);

        // Phase 1: Soil
        Results soilResult = fillSoilPhase(gui, context, mulchArea, potsToFill);
        if (!soilResult.IsSuccess()) return soilResult;

        // Return excess soil to mulch zone
        returnExcessSoil(gui, mulchArea);

        // Phase 2: Water (using barrel from waterForTrees zone)
        Results waterResult = fillWaterPhase(gui, context, potsToFill);
        if (!waterResult.IsSuccess()) return waterResult;

        gui.msg("Fill Tree Pots complete!");
        return Results.SUCCESS();
    }

    private ArrayList<WItem> filterPotsNeedingFill(ArrayList<WItem> pots) {
        ArrayList<WItem> result = new ArrayList<>();
        for (WItem w : pots) {
            if (w != null && w.item != null && (needsSoil(w) || needsWater(w))) {
                result.add(w);
            }
        }
        return result;
    }

    private boolean needsSoil(WItem pot) {
        return getSoilCurrent(pot) < 4;
    }

    /** Returns current soil count (0-4) or 0 if unknown. */
    private int getSoilCurrent(WItem pot) {
        if (pot == null || pot.item == null || pot.item.info == null) return 0;
        for (ItemInfo inf : pot.item.info) {
            if (inf instanceof ItemInfo.AdHoc) {
                ItemInfo.AdHoc ad = (ItemInfo.AdHoc) inf;
                if (ad.str != null && ad.str instanceof Text.Line) {
                    String text = ((Text.Line) ad.str).text;
                    if (text != null && text.contains("Soil:")) {
                        try {
                            String[] parts = text.split("Soil:");
                            if (parts.length > 1) {
                                String val = parts[1].trim().split("/")[0].trim();
                                return Math.min(4, Math.max(0, Integer.parseInt(val)));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return 0;
    }

    /** Total soil needed for pots that need soil. */
    private int countSoilNeeded(ArrayList<WItem> pots) {
        int total = 0;
        for (WItem p : pots) {
            if (p != null && p.item != null && needsSoil(p)) {
                total += 4 - getSoilCurrent(p);
            }
        }
        return total;
    }

    private Results fillSoilPhase(NGameUI gui, NContext context, NArea mulchArea, ArrayList<WItem> pots) throws InterruptedException {
        int soilNeeded = countSoilNeeded(pots);
        if (soilNeeded <= 0) {
            gui.msg("No soil needed, skipping soil phase.");
            return Results.SUCCESS();
        }

        int freeSpace = gui.getInventory().getFreeSpace();
        if (freeSpace == 0) {
            return Results.ERROR("Inventory is full");
        }

        // Try stockpile-soil first
        ArrayList<Gob> soilPiles = Finder.findGobs(mulchArea, new NAlias("gfx/terobjs/stockpile-soil"));
        if (!soilPiles.isEmpty()) {
            int toTake = Math.min(soilNeeded, freeSpace);
            Gob nearestPile = soilPiles.get(0);
            new PathFinder(nearestPile).run(gui);
            new OpenTargetContainer("Stockpile", nearestPile).run(gui);
            NUtils.addTask(new WaitStockpile(true));
            TakeItemsFromPile takeFromPile = new TakeItemsFromPile(nearestPile, gui.getStockpile(), toTake);
            takeFromPile.run(gui);
            new CloseTargetWindow(gui.getWindow("Stockpile")).run(gui);
        } else {
            // Fallback: TakeItems2 for TAKE areas
            context.addInItem("Soil", null);
            new TakeItems2(context, "Soil", Math.min(soilNeeded, freeSpace)).run(gui);
        }

        if (gui.getInventory().getItems(SOIL).isEmpty()) {
            return Results.ERROR("No soil/mulch available in mulch zone");
        }

        // Fill each pot with 4 soil (use indices - needsSoil may have stale item.info)
        ArrayList<WItem> allPots = gui.getInventory().getItems(POT);
        ArrayList<Integer> indicesToFill = new ArrayList<>();
        for (int idx = 0; idx < allPots.size(); idx++) {
            if (needsSoil(allPots.get(idx))) indicesToFill.add(idx);
        }

        for (int i = 0; i < indicesToFill.size(); i++) {
            int potIdx = indicesToFill.get(i);
            int currentSoil = getSoilCurrent(allPots.get(potIdx));
            int soilForThisPot = 4 - currentSoil;
            ArrayList<WItem> soilItems = gui.getInventory().getItems(SOIL);
            if (soilItems.size() < soilForThisPot) {
                int remaining = 0;
                for (int k = i; k < indicesToFill.size(); k++) {
                    remaining += 4 - getSoilCurrent(allPots.get(indicesToFill.get(k)));
                }
                int toTake = Math.min(remaining, gui.getInventory().getFreeSpace());
                if (toTake > 0) {
                    if (!soilPiles.isEmpty()) {
                        Gob pile = soilPiles.get(0);
                        new PathFinder(pile).run(gui);
                        new OpenTargetContainer("Stockpile", pile).run(gui);
                        NUtils.addTask(new WaitStockpile(true));
                        new TakeItemsFromPile(pile, gui.getStockpile(), toTake).run(gui);
                        new CloseTargetWindow(gui.getWindow("Stockpile")).run(gui);
                    } else {
                        new TakeItems2(context, "Soil", toTake).run(gui);
                    }
                }
            }

            ArrayList<WItem> currentPots = gui.getInventory().getItems(POT);
            if (potIdx >= currentPots.size()) break;
            WItem pot = currentPots.get(potIdx);

            for (int j = 0; j < soilForThisPot; j++) {
                WItem soil = gui.getInventory().getItem(SOIL);
                if (soil == null) break;
                NUtils.takeItemToHand(soil);
                pot.item.wdgmsg("itemact", 0);
                gui.ui.core.addTask(new HandIsFree(gui.getInventory()));
            }
        }

        return Results.SUCCESS();
    }

    /** Puts leftover soil back into the mulch zone. */
    private void returnExcessSoil(NGameUI gui, NArea mulchArea) throws InterruptedException {
        if (gui.getInventory().getItems(SOIL).isEmpty()) return;
        Pair<Coord2d, Coord2d> rc = mulchArea.getRCArea();
        if (rc == null) rc = mulchArea.getRCAreaFromStoredData();
        if (rc != null) new TransferToPiles(rc, SOIL).run(gui);
    }

    private Results fillWaterPhase(NGameUI gui, NContext context, ArrayList<WItem> pots) throws InterruptedException {
        NArea waterArea = context.getSpecArea(Specialisation.SpecName.waterForTrees);
        if (waterArea == null) {
            return Results.ERROR("No 'Water for Trees' zone found. Configure waterForTrees specialization.");
        }

        ArrayList<Gob> barrels = Finder.findGobs(waterArea, new NAlias("barrel"));
        Gob waterBarrel = null;
        for (Gob b : barrels) {
            if (NUtils.barrelHasContent(b) && NParser.checkName(NUtils.getContentsOfBarrel(b), "water")) {
                waterBarrel = b;
                break;
            }
        }
        if (waterBarrel == null) {
            return Results.ERROR("No barrel with water in Water for Trees zone.");
        }

        new PathFinder(waterBarrel).run(gui);

        // Find and fill each pot needing water (order changes when dropping back - use fresh find each time)
        WItem pot;
        while ((pot = findPotNeedingWater(gui.getInventory().getItems(POT))) != null) {
            NUtils.takeItemToHand(pot);
            NUtils.activateItem(waterBarrel);
            gui.ui.core.addTask(new WaitPotFilled(gui.vhand, 1.0));
            NUtils.dropToInv();
            gui.ui.core.addTask(new HandIsFree(gui.getInventory()));
        }

        return Results.SUCCESS();
    }

    private boolean needsWater(WItem pot) {
        if (pot.item.info == null) return true;
        for (ItemInfo inf : pot.item.info) {
            if (inf instanceof ItemInfo.AdHoc) {
                ItemInfo.AdHoc ad = (ItemInfo.AdHoc) inf;
                if (ad.str != null && ad.str instanceof Text.Line) {
                    String text = ((Text.Line) ad.str).text;
                    if (text != null && text.contains("Water:")) {
                        try {
                            String[] parts = text.split("Water:");
                            if (parts.length > 1) {
                                String val = parts[1].trim().split("/")[0].trim();
                                double current = Double.parseDouble(val);
                                return current < 1.0;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return true;
    }

    private WItem findPotNeedingWater(ArrayList<WItem> pots) {
        for (WItem p : pots) {
            if (p != null && p.item != null && needsWater(p)) return p;
        }
        return null;
    }
}
