package nurgling.actions;

import haven.Gob;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.res.ui.barterbox.Shopbox;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.areas.NContext;
import nurgling.tasks.WaitItems;
import nurgling.tasks.WindowIsClosed;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.util.ArrayList;

public class TransferToBarter implements Action{

    NAlias items;
    NContext.Barter barter;
    int th = 1;
    String exactName = null;
    double minQuality = 1.0;
    Double maxQualityExclusive = null;

    public TransferToBarter(NContext.Barter barter, NAlias items) {
        this.barter = barter;
        this.items = items;
    }

    public TransferToBarter(NContext.Barter barter, NAlias items, int th) {
        this.barter = barter;
        this.items = items;
        this.th = th;
    }

    public TransferToBarter(NContext.Barter barter, String exactName,
                            double minQuality, Double maxQualityExclusive) {
        this.barter = barter;
        this.exactName = exactName;
        this.items = new NAlias(exactName);
        this.minQuality = minQuality;
        this.maxQualityExclusive = maxQualityExclusive;
        this.th = (int)minQuality;
    }


    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Gob barterGob = Finder.findGob(barter.barter);
        Gob chestGob = Finder.findGob(barter.chest);

        ArrayList<WItem> wItems = getMatchingItems(gui);
        while (!wItems.isEmpty()) {
            int before = wItems.size();
            new PathFinder(barterGob).run(gui);
            new OpenTargetContainer("Barter Stand", barterGob).run(gui);

            Window barter_wnd = gui.getWindow("Barter Stand");
            if (barter_wnd == null) {
                return Results.ERROR("No Barter window");
            }

            boolean matchedOffer = false;
            for (Widget ch = barter_wnd.child; ch != null; ch = ch.next) {
                if (ch instanceof Shopbox) {
                    Shopbox sb = (Shopbox) ch;
                    Shopbox.ShopItem price = sb.getPrice();
                    if (matchesPrice(price, exactName, items)) {
                        matchedOffer = true;

                        int startSize = gui.getInventory().getItems("Branch").size();
                        int target_size = (sb.leftNum != 0) ? Math.min(wItems.size(), sb.leftNum) : wItems.size();
                        for (int i = 0; i < target_size; i++) {
                            sb.wdgmsg("buy", new Object[0]);
                        }
                        NUtils.getUI().core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias("Branch"), target_size + startSize));
                        new PathFinder(chestGob).run(gui);
                        new OpenTargetContainer("Chest", chestGob).run(gui);
                        ArrayList<WItem> branchitems = gui.getInventory().getItems("Branch");
                        new SimpleTransferToContainer(gui.getInventory("Chest"), gui.getInventory().getItems("Branch"), branchitems.size()-startSize).run(gui);
                        wItems = getMatchingItems(gui);
                        Window wnd = NUtils.getGameUI().getWindow("Chest");
                        if(wnd!=null) {
                            wnd.wdgmsg("close");
                            gui.ui.core.addTask(new WindowIsClosed(wnd));
                        }
                        break;
                    }
                }
            }
            if (!matchedOffer)
                return Results.ERROR("No matching barter offer for "
                        + (exactName != null ? exactName : items));
            if (!madeProgress(before, wItems.size()))
                return Results.ERROR("Barter did not consume "
                        + (exactName != null ? exactName : items));
        }
        return Results.SUCCESS();
    }

    static boolean matchesPrice(Shopbox.ShopItem price, String exactName, NAlias items) {
        if (price == null || price.name == null)
            return false;
        return exactName != null ? exactName.equals(price.name) : NParser.checkName(price, items);
    }

    static boolean madeProgress(int before, int after) {
        return after < before;
    }

    private ArrayList<WItem> getMatchingItems(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> allItems = gui.getInventory().getItems(items);
        if (exactName == null)
            return gui.getInventory().getItems(items, th);
        ArrayList<WItem> result = new ArrayList<>();
        for (WItem witem : allItems) {
            NGItem item = (NGItem)witem.item;
            double quality = item.quality != null ? item.quality : 1.0;
            if (exactName.equals(item.name()) && TransferItems2.matchesQuality(
                    quality, minQuality, maxQualityExclusive)) {
                result.add(witem);
            }
        }
        return result;
    }

}
