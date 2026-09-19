package nurgling.actions.bots;

import haven.Gob;
import haven.Resource;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.*;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tools.NAlias;

public class CollectSameItemsFromEarth implements Action {

    NAlias itemName;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        NContext context = new NContext(gui);

        SelectGob selgob;
        NUtils.getGameUI().msg("Please select item for pile");
        (selgob = new SelectGob(Resource.loadsimg("baubles/selectItem"))).run(gui);
        Gob target = selgob.result;
        if(target==null)
        {
            return Results.ERROR("Item not found");
        }
        if (target.ngob == null || target.ngob.name == null || target.ngob.name.trim().isEmpty())
        {
            return Results.ERROR("Item type not found");
        }

        // The inventory sample is optional: if it stacks into an existing slot, waiting for a
        // second WItem never completes. The selected gob resource is stable and is also used as
        // the exact ground filter below.
        itemName = new NAlias(target.ngob.name);
        addResourceLeaf(itemName, target.ngob.name);

        String insaId = context.createArea("Please select area with items", Resource.loadsimg("baubles/inputArea"));
        NArea insaArea = context.goToAreaById(insaId);
        String outsaId = context.createArea("Please select area for piles", Resource.loadsimg("baubles/outputArea"));
        NArea outsaArea = context.goToAreaById(outsaId);

        new PathFinder(target).run(gui);
        NUtils.rclickGob(target);
        NUtils.addTask(new nurgling.tasks.NoGob(target.id));

        new CollectItemsToPile(insaArea.getRCArea(), outsaArea.getRCArea(), itemName,
                new NAlias(target.ngob.name)).run(gui);
        return Results.SUCCESS();
    }

    private static void addResourceLeaf(NAlias alias, String resourceName) {
        int slash = resourceName.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < resourceName.length()) {
            String leaf = resourceName.substring(slash + 1);
            if (!alias.keys.contains(leaf)) {
                alias.keys.add(leaf);
                alias.buildCaches();
            }
        }
    }
}
