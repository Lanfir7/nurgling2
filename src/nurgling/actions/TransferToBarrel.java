package nurgling.actions;

import haven.*;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tasks.*;
import nurgling.tools.NAlias;

import java.util.ArrayList;

public class TransferToBarrel implements Action{

    Gob barrel;
    NAlias items;

    int th = 9000;

    double total = 0;

    boolean stalled = false;

    // When set, use exact name matching instead of NAlias substring matching
    String exactName = null;

    double minQuality = 1.0;
    Double maxQualityExclusive = null;
    boolean qualityFiltered = false;

    public TransferToBarrel(Gob barrel, NAlias items) {
        this.barrel = barrel;
        this.items = items;
    }

    public TransferToBarrel(Gob barrel, NAlias items, int th) {
        this(barrel, items);
        this.th = th;
    }

    public TransferToBarrel(Gob barrel, String exactName) {
        this.barrel = barrel;
        this.exactName = exactName;
        this.items = new NAlias(exactName);
    }

    public TransferToBarrel(Gob barrel, String exactName,
                            double minQuality, Double maxQualityExclusive) {
        this(barrel, exactName);
        this.minQuality = minQuality;
        this.maxQualityExclusive = maxQualityExclusive;
        this.qualityFiltered = true;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {

        if(barrel==null){
            return Results.ERROR("NULL BARREL");
        }
        if(getMatchingItems(gui).isEmpty() && !isHoldingTarget(gui)) {
            // Nothing to deposit - opening the barrel would only leave a stray window behind for
            // the next barrel in the area to be confused with.
            return Results.SUCCESS();
        }

        new PathFinder( barrel ).run (gui);
        // Right-clicking a gob while holding an item does not open it, so anything still in hand
        // from a previous barrel has to go back to the inventory first.
        returnHandToInventory(gui);
        if ( !(new OpenTargetContainer (  "Barrel",barrel ).run ( gui ).isSuccess) ) {
            return Results.ERROR("OPEN FAIL");
        }
        double barrelCont = gui.getBarrelContent();
        total+=Math.max(barrelCont, 0);

        if(barrelCont>-1 && barrelCont < th) {
            transfer(gui);
        }

        returnHandToInventory(gui);
        new CloseTargetContainer ( "Barrel" ).run ( gui );
        return Results.SUCCESS();
    }

    /**
     * Empties every matching item into the barrel, one dropsame at a time.
     *
     * The old implementation only ever considered items carrying a {@link GItem.Amount} or a
     * {@code CustomName} - i.e. stacked barrel produce and liquid containers. Anything else (ash
     * out of a kiln, for instance) matched neither, so it was left out of the transfer list and the
     * "some items are transferable" branch deposited a single item and then walked to the next
     * barrel with the rest still in the inventory. Deposit everything and let the barrel itself say
     * when it is full.
     */
    private void transfer(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> witems;
        while(!(witems = getMatchingItems(gui)).isEmpty()) {
            boolean handed = isHoldingTarget(gui);
            int before = witems.size() + (handed ? 1 : 0);

            if(!handed)
                NUtils.takeItemToHand(witems.get(0));
            if (qualityFiltered) {
                NUtils.activateItem(barrel, false);
                NUtils.addTask(new WaitFreeHand(80, false));
                if (isHoldingTarget(gui)) {
                    stalled = true;
                    break;
                }
                total += 1;
            } else {
                NUtils.dropsame(barrel);
                WaitItemsDecrease wait = new WaitItemsDecrease(
                        gui.getInventory(), items, exactName, before);
                NUtils.addTask(wait);
                if(!wait.decreased()) {
                    // The barrel refused the item: it is full, or it will not take this content.
                    stalled = true;
                    System.out.println("TransferToBarrel: barrel " + barrel.id + " stopped accepting, "
                            + witems.size() + " item(s) left");
                    break;
                }
                total += before - (getMatchingItems(gui).size()
                        + (isHoldingTarget(gui) ? 1 : 0));
            }
        }
    }

    /** Puts whatever is in the hand back into the inventory, so the next click can open a window. */
    private void returnHandToInventory(NGameUI gui) throws InterruptedException {
        NUtils.dropToInv(gui.getInventory());
    }

    private boolean isHoldingTarget(NGameUI gui) {
        WItem hand = NUtils.getGameUI().vhand;
        if(hand == null)
            return false;
        String name = ((NGItem) hand.item).name();
        if(name == null)
            return false;
        if (exactName != null && !name.equals(exactName))
            return false;
        if (exactName == null && !nurgling.tools.NParser.checkName(name, items))
            return false;
        if (!qualityFiltered)
            return true;
        Float quality = ((NGItem)hand.item).quality;
        return TransferItems2.matchesQuality(
                quality != null ? quality : 1.0, minQuality, maxQualityExclusive);
    }

    public boolean isFull()
    {
        return stalled || total>th;
    }

    /**
     * Gets items from inventory, using exact name match if exactName is set,
     * otherwise uses NAlias substring matching.
     */
    private ArrayList<WItem> getMatchingItems(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> allItems = gui.getInventory().getItems(items);
        ArrayList<WItem> exactMatches = new ArrayList<>();
        for (WItem witem : allItems) {
            NGItem item = (NGItem)witem.item;
            boolean nameMatches = exactName == null || item.name().equals(exactName);
            Float quality = item.quality;
            boolean qualityMatches = !qualityFiltered || TransferItems2.matchesQuality(
                    quality != null ? quality : 1.0, minQuality, maxQualityExclusive);
            if (nameMatches && qualityMatches) {
                exactMatches.add(witem);
            }
        }
        return exactMatches;
    }
}
