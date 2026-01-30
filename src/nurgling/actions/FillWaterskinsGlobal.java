package nurgling.actions;

import haven.*;
import nurgling.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tasks.HandIsFree;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitItemContent;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.widgets.NEquipory;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

/**
 * Fills waterskins using global water zone with chunk navigation.
 * Finds and navigates to the global water zone if it exists, otherwise shows an error.
 */
public class FillWaterskinsGlobal implements Action {

    public FillWaterskinsGlobal() {}

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Pair<Coord2d, Coord2d> area = null;

        // Find global water zone
        NArea.Specialisation waterSpec = new NArea.Specialisation(Specialisation.SpecName.water.toString());
        NArea nArea = NContext.findSpecGlobal(waterSpec);

        if (nArea == null) {
            return Results.ERROR("No global water zone found. Please create a water zone first.");
        }

        // Navigate to the global water zone using chunk navigation
        NUtils.navigateToArea(nArea);
        area = nArea.getRCArea();

        Gob target = null;
        String targetContent = null; // Track what's in the target (water or tea)
        if (area != null) {
            ArrayList<Gob> targets = Finder.findGobs(area, new NAlias("barrel", "cistern", "well"));
            // First, try to find barrel with water or tea
            for (Gob cand : targets) {
                if (NParser.isIt(cand, new NAlias("barrel"))) {
                    if (NUtils.barrelHasContent(cand)) {
                        String content = NUtils.getContentsOfBarrel(cand);
                        if (NParser.checkName(content, "water") || NParser.checkName(content, "tea")) {
                            target = cand;
                            targetContent = content;
                            break;
                        }
                    }
                }
            }
            // If no barrel with content found, use cistern or well (water)
            if (target == null) {
                for (Gob cand : targets) {
                    if (!NParser.isIt(cand, new NAlias("barrel"))) {
                        target = cand;
                        targetContent = "water";
                        break;
                    }
                }
            }
            if (target == null)
                return Results.ERROR("No containers with water or tea");
        } else {
            return Results.ERROR("no water area");
        }
        WItem wbelt = NUtils.getEquipment().findItem(NEquipory.Slots.BELT.idx);
        boolean needPf = true;
        if (wbelt != null) {
            if (wbelt.item.contents instanceof NInventory) {
                // Fill waterskins if source has water
                if (targetContent != null && (targetContent.contains("water") || targetContent.contains("Water"))) {
                    ArrayList<WItem> witems = ((NInventory) wbelt.item.contents).getItems(new NAlias("Waterskin"));
                    if (!witems.isEmpty() && target != null) {
                        needPf = false;
                        new PathFinder(target).run(gui);
                    }
                    for (WItem item : witems) {
                        NGItem ngItem = ((NGItem) item.item);
                        if (ngItem.content().isEmpty()) {
                            NUtils.takeItemToHand(item);
                            NUtils.activateItem(target);
                            NUtils.getUI().core.addTask(new WaitItemContent(NUtils.getGameUI().vhand));
                            NUtils.transferToBelt();
                            NUtils.getUI().core.addTask(new HandIsFree(((NInventory) wbelt.item.contents)));
                        }
                    }
                }
                // Fill teapots if source has tea
                if (targetContent != null && (targetContent.contains("tea") || targetContent.contains("Tea"))) {
                    ArrayList<WItem> teapots = ((NInventory) wbelt.item.contents).getItems(new NAlias("Teapot"));
                    if (!teapots.isEmpty() && target != null)
                        new PathFinder(target).run(gui);
                    for (WItem item : teapots) {
                        NGItem ngItem = ((NGItem) item.item);
                        if (ngItem.content().isEmpty()) {
                            NUtils.takeItemToHand(item);
                            NUtils.activateItem(target);
                            NUtils.getUI().core.addTask(new WaitItemContent(NUtils.getGameUI().vhand));
                            NUtils.transferToBelt();
                            NUtils.getUI().core.addTask(new HandIsFree(((NInventory) wbelt.item.contents)));
                        }
                    }
                }
            }
        }
        if (needPf)
            new PathFinder(target).run(gui);
        refillItemInEquip(gui, NUtils.getEquipment().findItem(NEquipory.Slots.LFOOT.idx), target, targetContent);
        refillItemInEquip(gui, NUtils.getEquipment().findItem(NEquipory.Slots.RFOOT.idx), target, targetContent);
        // Refill buckets in hands
        refillBucketInHand(gui, NUtils.getEquipment().findItem(NEquipory.Slots.HAND_LEFT.idx), target, targetContent);
        refillBucketInHand(gui, NUtils.getEquipment().findItem(NEquipory.Slots.HAND_RIGHT.idx), target, targetContent);
        return Results.SUCCESS();
    }

    void refillItemInEquip(NGameUI gui, WItem item, Gob target, String targetContent) throws InterruptedException {
        if (NParser.isIt(target, new NAlias("barrel"))) {
            if (!NUtils.barrelHasContent(target)) {
                return;
            }
            String content = NUtils.getContentsOfBarrel(target);
            if (!NParser.checkName(content, "water") && !NParser.checkName(content, "tea")) {
                return;
            }
        }
        if (item != null && item.item instanceof NGItem) {
            NGItem ngItem = ((NGItem) item.item);
            String itemName = ngItem.name();
            // Fill waterskins/jugs with water
            if (NParser.checkName(itemName, new NAlias("Waterskin", "Glass Jug"))) {
                if (targetContent != null && (targetContent.contains("water") || targetContent.contains("Water"))) {
                    if (ngItem.content().isEmpty()) {
                        NUtils.takeItemToHand(item);
                        NUtils.activateItem(target);
                        NUtils.getUI().core.addTask(new WaitItemContent(NUtils.getGameUI().vhand));
                        NUtils.getEquipment().wdgmsg("drop", -1);
                        NUtils.addTask(new NTask() {
                            @Override
                            public boolean check() {
                                return NUtils.getGameUI().vhand == null;
                            }
                        });
                    }
                }
            }
            // Fill teapots with tea
            else if (NParser.checkName(itemName, "Teapot")) {
                if (targetContent != null && (targetContent.contains("tea") || targetContent.contains("Tea"))) {
                    if (ngItem.content().isEmpty()) {
                        NUtils.takeItemToHand(item);
                        NUtils.activateItem(target);
                        NUtils.getUI().core.addTask(new WaitItemContent(NUtils.getGameUI().vhand));
                        NUtils.getEquipment().wdgmsg("drop", -1);
                        NUtils.addTask(new NTask() {
                            @Override
                            public boolean check() {
                                return NUtils.getGameUI().vhand == null;
                            }
                        });
                    }
                }
            }
        }
    }

    void refillBucketInHand(NGameUI gui, WItem item, Gob target, String targetContent) throws InterruptedException {
        if (target == null) return;
        if (NParser.isIt(target, new NAlias("barrel"))) {
            if (!NUtils.barrelHasContent(target)) {
                return;
            }
            String content = NUtils.getContentsOfBarrel(target);
            if (!NParser.checkName(content, "water") && !NParser.checkName(content, "tea")) {
                return;
            }
        }
        if (item != null && item.item instanceof NGItem && NParser.checkName(((NGItem) item.item).name(), "Bucket")) {
            // Buckets only for water
            if (targetContent != null && !targetContent.contains("water") && !targetContent.contains("Water")) {
                return;
            }
            NGItem ngItem = ((NGItem) item.item);
            // Refill if bucket is empty or has water but not full (not "10l")
            boolean needRefill = ngItem.content().isEmpty();
            if (!needRefill) {
                String contentName = ngItem.content().get(0).name();
                // Has water but not full (full bucket shows "10l of Water")
                if (contentName.contains("Water") && !contentName.contains("10l")) {
                    needRefill = true;
                }
            }
            if (needRefill) {
                NUtils.takeItemToHand(item);
                NUtils.activateItem(target);
                NUtils.getUI().core.addTask(new WaitItemContent(NUtils.getGameUI().vhand));
                NUtils.getEquipment().wdgmsg("drop", -1);
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        return NUtils.getGameUI().vhand == null;
                    }
                });
            }
        }
    }
}
