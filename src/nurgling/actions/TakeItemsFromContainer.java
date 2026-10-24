package nurgling.actions;

import haven.Coord;
import haven.Inventory;
import haven.WItem;
import nurgling.*;
import nurgling.NInventory.QualityType;
import nurgling.tasks.WaitItems;
import nurgling.tools.Container;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.StackSupporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.function.Predicate;

public class TakeItemsFromContainer implements Action
{
    Coord target_coord = new Coord(1,1);
    Container cont;
    HashSet<String> names;
    NAlias pattern;
    QualityType qualityType;
    public int minSize = Integer.MAX_VALUE;
    public boolean exactMatch = false;
    public Predicate<NGItem> filter = null;
    public TakeItemsFromContainer(Container cont, HashSet<String> names, NAlias pattern)
    {
        this.cont = cont;
        this.names = names;
        this.pattern = pattern;
        this.qualityType = null;
    }

    double targetq = -1;
    public TakeItemsFromContainer(Container cont, HashSet<String> names, NAlias pattern, double q)
    {
        this.cont = cont;
        this.names = names;
        this.pattern = pattern;
        this.targetq = q;
        this.qualityType = null;
    }

    public TakeItemsFromContainer(Container cont, HashSet<String> names, NAlias pattern, QualityType qualityType)
    {
        this.cont = cont;
        this.names = names;
        this.pattern = pattern;
        this.qualityType = qualityType;
    }

    int target_size = 0;
    boolean took = false;
    private int movedCount = 0;

    // Quality comparators for sorting items (following GetItems pattern but with null safety)
    private Comparator<WItem> high = new Comparator<WItem>() {
        @Override
        public int compare(WItem lhs, WItem rhs) {
            Float qualityL = ((NGItem) lhs.item).quality;
            Float qualityR = ((NGItem) rhs.item).quality;
            if (qualityL == null && qualityR == null) return 0;
            if (qualityL == null) return 1;
            if (qualityR == null) return -1;
            return Double.compare(qualityR, qualityL);
        }
    };

    private Comparator<WItem> low = new Comparator<WItem>() {
        @Override
        public int compare(WItem lhs, WItem rhs) {
            Float qualityL = ((NGItem) lhs.item).quality;
            Float qualityR = ((NGItem) rhs.item).quality;
            if (qualityL == null && qualityR == null) return 0;
            if (qualityL == null) return 1;
            if (qualityR == null) return -1;
            return Double.compare(qualityL, qualityR);
        }
    };
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        movedCount = 0;
        NInventory inv = gui.getInventory(cont.cap);
        int remainingToTake = minSize; // Track how many items we still need to take
        
        for(String name: names) {
            if (remainingToTake <= 0) {
                break; // Already took enough items
            }
            
            WItem item = inv.getItem(name);
            if (item != null) {

                target_coord = inv.getItem(name).sz.div(Inventory.sqsz);
                int oldSpace = held(gui, name);
                int oldAll = gui.getInventory().getItems(name).size();
                ArrayList<WItem> items = getItems(gui, name);
                int items_size = items.size();
                target_size = Math.min(remainingToTake, Math.min(gui.getInventory().getNumberFreeCoord(target_coord.swapXY())*StackSupporter.getFullStackSize(name), items.size()));


                int temptr = target_size;

                for (int i = 0; i < temptr; i++) {
                    int left = target_size + oldSpace - held(gui, name);
                    int moved = TransferToContainer.transfer(items.get(i), gui.getInventory(), left, filter != null);
                    if (moved <= 0)
                        break;
                    items = getItems(gui, name);
                    if(held(gui, name)>=target_size+oldSpace || items.isEmpty())
                        break;
                    temptr=Math.min(remainingToTake, Math.min(gui.getInventory().getNumberFreeCoord(target_coord.swapXY())*StackSupporter.getFullStackSize(name), items.size()));
                    i = -1;
                }
                if (filter == null) {
                    WaitItems wi = new WaitItems(gui.getInventory(), new NAlias(name), oldAll + target_size);
                    NUtils.getUI().core.addTask(wi);
                }
                cont.update();
                
                // Reduce remainingToTake by how many we actually took
                int actuallyTaken = held(gui, name) - oldSpace;
                movedCount += Math.max(0, actuallyTaken);
                remainingToTake -= actuallyTaken;
                if (filter != null && target_size > 0 && actuallyTaken <= 0)
                    return Results.FAIL();
                
                if(items_size>target_size) {
                    took = false;
                    return Results.FAIL();
                }
            }
        }
        took = true;
        return Results.SUCCESS();
    }

    private int held(NGameUI gui, String name) throws InterruptedException {
        ArrayList<WItem> items = gui.getInventory().getItems(name);
        if (filter == null)
            return items.size();
        int count = 0;
        for (WItem item : items)
            if (filter.test((NGItem) item.item)) count++;
        return count;
    }

    private ArrayList<WItem> getItems(NGameUI gui, String name) throws InterruptedException
    {
        ArrayList<WItem> items = gui.getInventory(cont.cap).getItems(name,1);
        HashSet<WItem> forRemove = new HashSet<>();

        for(WItem item1: items) {
            // Check for exact name match if exactMatch flag is set
            if (exactMatch && item1.item instanceof NGItem) {
                String itemName = ((NGItem) item1.item).name();
                if (itemName != null && !itemName.equalsIgnoreCase(name)) {
                    forRemove.add(item1);
                    continue;
                }
            }
            if (filter != null && !filter.test((NGItem) item1.item)) {
                forRemove.add(item1);
                continue;
            }
            
            if (pattern != null) {
                if (!NParser.checkName(((NGItem) item1.item).name(), pattern)) {
                    forRemove.add(item1);
                }
            }
            if(targetq!=-1)
            {
                if(((NGItem) item1.item).quality>targetq)
                {
                    forRemove.add(item1);
                }
            }
        }
        items.removeAll(forRemove);

        // Apply quality sorting if priority is specified
        if (qualityType != null) {
            if (qualityType == QualityType.High) {
                Collections.sort(items, high);
            } else if (qualityType == QualityType.Low) {
                Collections.sort(items, low);
            }
        }

        return items;
    }

    public boolean getResult()
    {
        return took;
    }

    public int getTarget_size() {
        return target_size;
    }

    public int getMovedCount() {
        return movedCount;
    }
}
