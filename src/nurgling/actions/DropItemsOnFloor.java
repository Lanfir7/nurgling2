package nurgling.actions;

import haven.Coord2d;
import haven.Pair;
import haven.WItem;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.SoilFloorDump;
import nurgling.tasks.GetWItems;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitFreeHand;
import nurgling.tools.NAlias;

import java.util.ArrayList;

public class DropItemsOnFloor implements Action {
    static final String DROP_SAME = "drop-same";
    static final boolean DROP_SAME_ASCENDING = false;

    private final Pair<Coord2d, Coord2d> area;
    private final NAlias items;
    private final String exactName;
    private final double minQuality;
    private final Double maxQualityExclusive;
    private final boolean qualityFiltered;

    public DropItemsOnFloor(Pair<Coord2d, Coord2d> area, String itemName) {
        this.area = area;
        this.items = new NAlias(itemName);
        this.exactName = itemName;
        this.minQuality = 1.0;
        this.maxQualityExclusive = null;
        this.qualityFiltered = false;
    }

    public DropItemsOnFloor(Pair<Coord2d, Coord2d> area, String itemName,
                            double minQuality, Double maxQualityExclusive) {
        this.area = area;
        this.items = new NAlias(itemName);
        this.exactName = itemName;
        this.minQuality = minQuality;
        this.maxQualityExclusive = maxQualityExclusive;
        this.qualityFiltered = true;
    }

    static boolean shouldKeepDropping(int remaining) {
        return remaining > 0;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Coord2d center = SoilFloorDump.center(area);
        if (center == null)
            return Results.FAIL();
        new PathFinder(center).run(gui);
        if (gui.vhand != null) {
            NUtils.drop(gui.vhand);
            NUtils.addTask(new WaitFreeHand());
        }
        ArrayList<WItem> slots = qualityFiltered
                ? getMatchingItems(gui)
                : gui.getInventory().getWItems(items);
        if (!shouldKeepDropping(slots.size()))
            return Results.SUCCESS();
        if (qualityFiltered) {
            while (!(slots = getMatchingItems(gui)).isEmpty()) {
                WItem item = slots.get(0);
                NUtils.drop(item);
                NUtils.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        try {
                            return !getMatchingItems(gui).contains(item);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return true;
                        }
                    }
                });
            }
            return Results.SUCCESS();
        }
        WItem slot = slots.get(0);
        slot.wdgmsg(DROP_SAME, slot.item, DROP_SAME_ASCENDING);
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                GetWItems gi = new GetWItems(gui.getInventory(), items);
                if (!gi.check())
                    return false;
                return gi.getResult().isEmpty();
            }
        });
        return Results.SUCCESS();
    }

    private ArrayList<WItem> getMatchingItems(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> allItems = gui.getInventory().getItems(items);
        ArrayList<WItem> result = new ArrayList<>();
        for (WItem witem : allItems) {
            NGItem item = (NGItem)witem.item;
            double quality = item.quality != null ? item.quality : 1.0;
            if (exactName.equals(item.name()) && (!qualityFiltered
                    || TransferItems2.matchesQuality(
                    quality, minQuality, maxQualityExclusive))) {
                result.add(witem);
            }
        }
        return result;
    }
}
