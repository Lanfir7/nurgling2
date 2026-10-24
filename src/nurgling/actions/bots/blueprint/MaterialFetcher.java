package nurgling.actions.bots.blueprint;

import nurgling.i18n.L10n;

import haven.GItem;
import haven.WItem;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.actions.Results;
import nurgling.actions.TakeItems2;
import nurgling.areas.NContext;
import nurgling.conf.ConstructionMaterialsRegistry;
import nurgling.tools.NAlias;
import nurgling.widgets.Specialisation;

/** Fetches one confirmed material unit for the active construction sign. */
final class MaterialFetcher {
    private final NContext context;

    MaterialFetcher(NContext context) {
        this.context = context;
    }

    static int units(WItem item) {
        if (item == null || !(item.item instanceof NGItem) || item.item.contents != null)
            return 0;
        GItem.Amount amount = ((NGItem)item.item).getInfo(GItem.Amount.class);
        return amount == null ? 1 : Math.max(0, amount.itemnum());
    }

    static int countInInventory(NGameUI gui, String material) throws InterruptedException {
        NInventory inv = gui.getInventory();
        if (inv == null) return 0;
        long total = 0;
        for (WItem item : inv.getItems(BuildRecipes.aliasFor(material)))
            total += units(item);
        return (int)Math.min(Integer.MAX_VALUE, total);
    }

    /** False means no configured source; an attempted transfer without an exact receipt is uncertain. */
    boolean fetchOne(NGameUI gui, String material) throws InterruptedException,
            GhostConstruction.FatalException {
        if (gui.vhand != null || gui.map.placing != null)
            throw new GhostConstruction.FatalException(L10n.get("bbuilder.safe.busy"));
        int before = countInInventory(gui, material);
        if (before > 0) return true;
        if (gui.getInventory().getNumberFreeCoord(BuildRecipes.shapeOf(material)) <= 0)
            return false;
        NAlias alias = BuildRecipes.aliasFor(material);
        ConstructionMaterialsRegistry.MaterialType type = BuildRecipes.zoneTypeFor(material);
        Results result = null;
        if (type != null && ConstructionMaterialsRegistry.hasMaterialZone(type)) {
            TakeItems2 take = new TakeItems2(context, alias.getKeys().get(0), 1,
                    Specialisation.SpecName.buildMaterials, type.getSubtype());
            result = take.takeAny(alias, gui);
            BuildRecipes.observeShape(material, take.getObservedShape());
        } else {
            for (String name : alias.getKeys()) {
                if (NContext.findInGlobal(name) == null) continue;
                context.addInItem(name, null);
                TakeItems2 take = new TakeItems2(context, name, 1);
                take.exactMatch = true;
                result = take.run(gui);
                break;
            }
        }
        if (result == null) return false;
        if (!result.IsSuccess() || gui.vhand != null || countInInventory(gui, material) <= before)
            throw new GhostConstruction.FatalException(L10n.get("bbuilder.safe.material"));
        return true;
    }
}
