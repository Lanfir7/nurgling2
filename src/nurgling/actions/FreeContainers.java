package nurgling.actions;

import haven.Gob;
import haven.WItem;
import nurgling.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tools.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

public class FreeContainers implements Action
{
    ArrayList<Container> containers;
    NAlias pattern = null;
    NArea excludeOutArea = null;

    public FreeContainers(ArrayList<Container> containers) {
        this.containers = containers;
    }

    public FreeContainers(ArrayList<Container> containers, NAlias pattern) {
        this.containers = containers;
        this.pattern = pattern;
    }

    /**
     * @param excludeOutArea an area to skip as a PUT destination (e.g. the zone the containers
     *                       are themselves being emptied from), or null to consider every area.
     */
    public FreeContainers(ArrayList<Container> containers, NAlias pattern, NArea excludeOutArea) {
        this.containers = containers;
        this.pattern = pattern;
        this.excludeOutArea = excludeOutArea;
    }

    HashSet<String> targets = new HashSet<>();

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        NContext context = new NContext(gui);
        context.excludeOutArea(excludeOutArea);
        boolean hadKindSelection = false;

        for (Container container : containers)
        {
            Container.Space space;
            if ((space = container.getattr(Container.Space.class)).isReady())
            {
                if (space.getRes().get(Container.Space.FREESPACE) == space.getRes().get(Container.Space.MAXSPACE))
                    continue;
            }

            navigateToTargetContainer(gui, container);

            new OpenTargetContainer(container).run(gui);
            ArrayList<WItem> items = (pattern == null) ? gui.getInventory(container.cap).getItems() : gui.getInventory(container.cap).getItems(pattern);
            // NContext.addOutItem() caches the best-fitting area per item name and reuses it for
            // later calls whose quality merely exceeds an already-cached (possibly lower) threshold.
            // That cache is only correct if items are fed to it in descending quality order per name
            // (see FreeInventory2, which does the same sort) - otherwise a low-quality item scanned
            // first can lock in a low-threshold area, and a higher-quality item of the same name that
            // should go to a better zone gets routed there instead.
            items.sort(new Comparator<WItem>() {
                @Override
                public int compare(WItem o1, WItem o2) {
                    Float q1 = ((NGItem) o1.item).quality;
                    Float q2 = ((NGItem) o2.item).quality;
                    if (q1 == null && q2 == null)
                        return 0;
                    if (q1 == null)
                        return 1;
                    if (q2 == null)
                        return -1;
                    return Float.compare(q2, q1);
                }
            });
            boolean splitButter = items.stream().anyMatch(item -> ItemVariants.has(((NGItem) item.item).name()))
                    && context.hasVariantOutputInBand(ItemVariants.BUTTER, 0, null);
            if (splitButter && !ItemVariants.awaitKnown(items))
                return Results.ERROR("Butter kind is still loading; container items were kept.");
            HashSet<String> names = new HashSet<>();
            boolean kindSelectionNeeded = false;
            for (WItem item : items)
            {
                NGItem gi = (NGItem) item.item;
                double q = gi.quality != null ? gi.quality : 1;
                String route = splitButter ? context.outKey(gi, q) : gi.name();
                if (route != null && context.addOutItem(route, null, q)) {
                    targets.add(route);
                    names.add(gi.name());
                    kindSelectionNeeded |= ItemVariants.isKey(route);
                }
            }
            TakeItemsFromContainer take = new TakeItemsFromContainer(container, names, pattern);
            hadKindSelection |= kindSelectionNeeded;
            if (kindSelectionNeeded)
                take.filter = gi -> !ItemVariants.has(gi.name()) || targets.contains(
                        context.outKey(gi, gi.quality != null ? gi.quality : 1));
            int stalled = 0;
            while (true) {
                Results fetched = take.run(gui);
                if (fetched.IsSuccess())
                    break;
                if (kindSelectionNeeded && take.getMovedCount() == 0 && ++stalled >= 2)
                    return Results.ERROR("Could not take selected Butter from the container; items were kept.");
                if (take.getMovedCount() > 0)
                    stalled = 0;
                Results unloaded = new TransferItems2(context, targets).run(gui);
                if (kindSelectionNeeded && !unloaded.IsSuccess())
                    return unloaded;
                navigateToTargetContainer(gui, container);
                Results opened = new OpenTargetContainer(container).run(gui);
                if (kindSelectionNeeded && !opened.IsSuccess())
                    return opened;
            }
            new CloseTargetContainer(container).run(gui);
        }
        Results unloaded = new TransferItems2(context, targets).run(gui);
        if (hadKindSelection && !unloaded.IsSuccess())
            return unloaded;
        return Results.SUCCESS();
    }

    private void navigateToTargetContainer(NGameUI gui, Container container) throws InterruptedException {
        PathFinder pf;

        Gob gob = Finder.findGob(container.gobHash);
        if(gob!= null && PathFinder.isAvailable(gob)) {
            pf = new PathFinder(gob);
            pf.isHardMode = true;
            pf.run(gui);
        }
        else
        {
            if(container.parent!=null)
            {
                NUtils.navigateToArea(container.parent, true);
                gob = Finder.findGob(container.gobHash);
                if(gob!= null && PathFinder.isAvailable(gob)) {
                    pf = new PathFinder(gob);
                    pf.isHardMode = true;
                    pf.run(gui);
                }
            }
        }
    }
}
