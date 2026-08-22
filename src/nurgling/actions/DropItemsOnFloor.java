package nurgling.actions;

import haven.Coord2d;
import haven.Pair;
import haven.WItem;
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

    public DropItemsOnFloor(Pair<Coord2d, Coord2d> area, String itemName) {
        this.area = area;
        this.items = new NAlias(itemName);
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
        ArrayList<WItem> slots = gui.getInventory().getWItems(items);
        if (!shouldKeepDropping(slots.size()))
            return Results.SUCCESS();
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
}
