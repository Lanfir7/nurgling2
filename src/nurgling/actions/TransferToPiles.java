package nurgling.actions;

import haven.*;
import nurgling.ExtraInvGroupTransfer;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NISBox;
import nurgling.NUtils;
import nurgling.tasks.*;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.tools.StackSupporter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TransferToPiles implements Action{

    NAlias items;

    Pair<Coord2d,Coord2d> out;

    int th = 0;

    Double maxQualityExclusive = null;

    enum PileMode {
        GOB_SHIFT_BULK,
        TYPE_BULK,
        ONE_BY_ONE
    }

    static PileMode pileMode(int th, boolean mixedCategory) {
        if (th > 1) {
            return PileMode.ONE_BY_ONE;
        }
        if (mixedCategory) {
            return PileMode.TYPE_BULK;
        }
        return PileMode.GOB_SHIFT_BULK;
    }

    /** Stack dump leaves the last item; WaitTargetSize(fullSize - N) hangs at the pile. */
    static boolean useExactInventoryWait(PileMode mode) {
        return mode == PileMode.ONE_BY_ONE;
    }

    static boolean waitsForInventoryUpdate(PileMode mode) {
        return false;
    }

    /**
     * Mixed-category TYPE_BULK always uses invxf2 by exact type. Vanilla Shift-on-pile-gob
     * dumps every type the pile accepts, whether leftovers are stacked or loose (Quartz+Flint).
     */
    static boolean typeBulkUsesGobShift(boolean hasStackEntries) {
        return false;
    }

    /** invxf2 only for real stacks; leftover solos / 1-item stacks use transfer -1. */
    static boolean typeBulkSendsInvxf2(boolean stack, int stackSize) {
        return !ExtraInvGroupTransfer.isLeftover(stack, stackSize);
    }

    static List<ExtraInvGroupTransfer.Slot> typeBulkInvxf2Targets(List<ExtraInvGroupTransfer.Slot> matching) {
        if (matching == null || matching.isEmpty()) {
            return List.of();
        }
        ArrayList<ExtraInvGroupTransfer.Slot> out = new ArrayList<>();
        for (ExtraInvGroupTransfer.Slot slot : matching) {
            if (typeBulkSendsInvxf2(slot.stack, slot.stackSize)) {
                out.add(slot);
            }
        }
        return out;
    }

    /** Vanilla Alt+Shift: transfer all items of the clicked leftover's type. */
    static final String LEFTOVER_FLUSH_MSG = "transfer";
    static final int LEFTOVER_FLUSH_COUNT = -1;

    static ExtraInvGroupTransfer.Slot leftoverFlushTarget(List<ExtraInvGroupTransfer.Slot> slots, String exactName) {
        if (exactName == null) {
            return null;
        }
        List<ExtraInvGroupTransfer.Slot> leftovers = ExtraInvGroupTransfer.matchingLeftovers(
                slots, exactName, NInventory.Grouping.NONE);
        return leftovers.isEmpty() ? null : leftovers.get(0);
    }

    /**
     * After stack invxf2, wait until leftover count grows, or leftoverWatchDone on no-growth
     * scans. Do not fire transfer -1 on the first empty remnant check.
     */
    static boolean leftoverFlushReady(int pass, int leftoversThisPass, int leftoversBefore) {
        if (leftoversThisPass > leftoversBefore) {
            return true;
        }
        return ExtraInvGroupTransfer.leftoverWatchDone(pass, 0);
    }

    static boolean leftoverFlushSends(int leftoversThisPass) {
        return leftoversThisPass > 0;
    }

    static boolean stockpileFillChanged(int freeBefore, int freeNow) {
        return freeBefore >= 0 && freeNow >= 0 && freeNow < freeBefore;
    }

    static boolean stockpileTransferFinished(boolean sourceGone, boolean pileFull,
                                             int freeBefore, int freeNow) {
        return sourceGone || pileFull || stockpileFillChanged(freeBefore, freeNow);
    }

    /** Macro transfers must not fall back to a previously opened, possibly full stockpile. */
    static int[] typeBulkDestination(int pileWdgId) {
        return new int[]{pileWdgId};
    }

    // When set, use exact name matching instead of NAlias substring matching
    String exactName = null;

    public TransferToPiles(Pair<Coord2d,Coord2d> out, NAlias items) {
        this.out = out;
        this.items = items;
    }

    public TransferToPiles(Pair<Coord2d,Coord2d> out, NAlias items, int th) {
        this.out = out;
        this.items = items;
        this.th = th;
    }

    public TransferToPiles(Pair<Coord2d,Coord2d> out, String exactName, int th) {
        this.out = out;
        this.exactName = exactName;
        this.items = new NAlias(exactName);
        this.th = th;
    }

    public TransferToPiles(Pair<Coord2d,Coord2d> out, String exactName,
                           int th, Double maxQualityExclusive) {
        this(out, exactName, th);
        this.maxQualityExclusive = maxQualityExclusive;
    }


    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> witems;
        NAlias pileName;
        if (!(witems = getMatchingItems(gui)).isEmpty() ) {
                Gob target = null;
                for (Gob gob : Finder.findGobs(out, pileName = getStockpileName(items))) {
                    while (gob.ngob.getModelAttribute() != 31 && PathFinder.isAvailable(gob)) {
                            target = gob;
                            PathFinder pf = new PathFinder(target);
                            pf.isHardMode = true;
                            pf.run(gui);
                            if(NUtils.getGameUI().vhand!=null) {
                                NUtils.activateItem(target, false);
                                NUtils.addTask(new WaitFreeHand(80, false));
                            }
                            witems = getMatchingItems(gui);
                            int size = witems.size();
                            new OpenTargetContainer("Stockpile", target).run(gui);
                            int target_size = Math.min(size,gui.getStockpile().getFreeSpace());
                            if(target_size>0) {
                                if (!transfer(gui, target_size)) {
                                    return Results.FAIL();
                                }
                            }
                            if((witems = getMatchingItems(gui)).isEmpty())
                            {
                                new CloseTargetContainer("Stockpile").run(gui);
                                return Results.SUCCESS();
                            }
                        }
                }

                while(!getMatchingItems(gui).isEmpty() && out!=null) {
                    PileMaker pm;
                    if (exactName != null) {
                        pm = new PileMaker(out, exactName, pileName, th);
                    } else {
                        pm = new PileMaker(out, items, pileName, th);
                    }
                    if(!pm.run(gui).IsSuccess())
                        return Results.FAIL();
                    Gob pile = pm.getPile();
                    while (pile.ngob.getModelAttribute() != 31) {
                        witems = getMatchingItems(gui);
                        int size = witems.size();
                        new OpenTargetContainer("Stockpile", pile).run(gui);
                        int target_size = Math.min(size, gui.getStockpile().getFreeSpace());
                        if (target_size > 0) {
                            if (!transfer(gui, target_size)) {
                                return Results.FAIL();
                            }
                        }
                        else
                        {
                            break;
                        }
                    }
                }
            }
        return Results.SUCCESS();
        }

    private boolean transfer(NGameUI gui, int target_size) throws InterruptedException {
        NUtils.addTask(new WaitStockpile(true, 80, false));
        int fullSize = gui.getInventory().getItems().size();
        PileMode mode = maxQualityExclusive != null
                ? PileMode.ONE_BY_ONE
                : pileMode(th, sameCategoryItemPresent(gui));
        boolean accepted = true;
        if (mode == PileMode.ONE_BY_ONE) {
            transferOneByOne(gui, target_size);
        } else if (mode == PileMode.TYPE_BULK) {
            accepted = transferTypeBulk(gui, target_size);
        } else {
            accepted = transferHeldTypeToStockpileGob(gui, target_size);
        }
        if (!accepted) {
            return false;
        }

        if (useExactInventoryWait(mode)) {
            NUtils.getUI().core.addTask(new WaitTargetSize(NUtils.getGameUI().getInventory(), fullSize - target_size));
        }
        return true;
    }

    private void transferOneByOne(NGameUI gui, int target_size) throws InterruptedException {
        for (int i = 0; i < target_size; i++) {
            ArrayList<WItem> witems = getMatchingItems(gui);
            witems.sort(new Comparator<WItem>() {
                @Override
                public int compare(WItem o1, WItem o2) {
                    Float q1 = ((NGItem)o1.item).quality;
                    Float q2 = ((NGItem)o2.item).quality;
                    if(q1 == null || q2 == null)
                        return 0;
                    return Float.compare(q1,q2);
                }
            });
            if (witems.isEmpty()) {
                break;
            }
            NUtils.takeItemToHand(witems.get(0));
            gui.getStockpile().wdgmsg("drop");
            NUtils.addTask(new WaitFreeHand());
        }
    }

    /** Mixed types cannot use Stockpile.put or gob-shift (server/vanilla pick any sibling). */
    private boolean transferTypeBulk(NGameUI gui, int target_size) throws InterruptedException {
        NISBox pile = gui.getStockpile();
        if (pile == null || target_size < 1) {
            return false;
        }
        if (pile.parentGob != null) {
            monitoring.StockpileStorageTracker.touch(pile.parentGob);
        }
        int[] dest = typeBulkDestination(pile.wdgid());
        Object[] invxf2 = ExtraInvGroupTransfer.invxf2Args(dest);
        if (invxf2 == null) {
            return false;
        }
        ArrayList<WItem> matching = getMatchingItems(gui);
        int leftoversBefore = countLeftovers(matching);
        int sent = 0;
        boolean dumpedStacks = false;
        for (WItem w : matching) {
            if (sent >= target_size) {
                break;
            }
            if (w != null && w.item != null && !NInventory.isLeftoverItem(w)) {
                w.item.wdgmsg(ExtraInvGroupTransfer.EXTRA_SHIFT_MSG, invxf2);
                sent++;
                dumpedStacks = true;
            }
        }
        return flushTypeBulkLeftovers(gui, dumpedStacks, leftoversBefore, matching);
    }

    private boolean flushTypeBulkLeftovers(NGameUI gui, boolean dumpedStacks, int leftoversBefore,
                                           ArrayList<WItem> matching) throws InterruptedException {
        WItem leftover;
        int leftoverCount;
        if (dumpedStacks) {
            leftover = null;
            leftoverCount = leftoversBefore;
            int pass = 0;
            do {
                NUtils.addTask(new WaitTicks(ExtraInvGroupTransfer.LEFTOVER_DELAY_TICKS));
                pass++;
                matching = getMatchingItems(gui);
                leftover = findLeftover(matching);
                leftoverCount = countLeftovers(matching);
            } while (!leftoverFlushReady(pass, leftoverCount, leftoversBefore));
        } else {
            leftover = findLeftover(matching);
            leftoverCount = countLeftovers(matching);
        }
        if (!leftoverFlushSends(leftoverCount) || leftover == null) {
            return true;
        }
        GItem target = NInventory.leftoverTransferTarget(leftover);
        if (target == null) {
            return true;
        }
        target.wdgmsg(LEFTOVER_FLUSH_MSG, Inventory.sqsz.div(2), LEFTOVER_FLUSH_COUNT);
        return true;
    }

    private static WItem findLeftover(ArrayList<WItem> matching) {
        for (WItem w : matching) {
            if (w != null && NInventory.isLeftoverItem(w)) {
                return w;
            }
        }
        return null;
    }

    private static int countLeftovers(ArrayList<WItem> matching) {
        int n = 0;
        for (WItem w : matching) {
            if (w != null && NInventory.isLeftoverItem(w)) {
                n++;
            }
        }
        return n;
    }

    /** Matches taking an item in hand and Shift-clicking the stockpile gob. */
    private boolean transferHeldTypeToStockpileGob(NGameUI gui, int targetSize) throws InterruptedException {
        NISBox pile = gui.getStockpile();
        if (pile == null || targetSize < 1) {
            return false;
        }
        if (pile.parentGob == null) {
            pile.put(targetSize);
            return true;
        }
        ArrayList<WItem> matching = getMatchingItems(gui);
        if (matching.isEmpty()) {
            return true;
        }
        int freeBefore = pile.calcFreeSpace();
        if (freeBefore < 0) {
            return false;
        }
        monitoring.StockpileStorageTracker.touch(pile.parentGob);
        WItem source = matching.get(0);
        NUtils.takeItemToHand(source);
        NUtils.activateItem(pile.parentGob, true);
        NUtils.addTask(new WaitFreeHand(80, false));
        WaitStockpileFillChanged wait = new WaitStockpileFillChanged(pile, source, freeBefore);
        NUtils.addTask(wait);
        return wait.changed;
    }

    private static final class WaitStockpileFillChanged extends NTask {
        private final NISBox pile;
        private final WItem source;
        private final int freeBefore;
        boolean changed;

        WaitStockpileFillChanged(NISBox pile, WItem source, int freeBefore) {
            this.pile = pile;
            this.source = source;
            this.freeBefore = freeBefore;
            this.infinite = false;
            this.maxCounter = 80;
            this.criticalOnTimeout = false;
        }

        @Override
        public boolean check() {
            boolean sourceGone = source == null || source.parent == null;
            boolean pileFull = pile.parentGob != null && pile.parentGob.ngob.getModelAttribute() == 31;
            changed = stockpileTransferFinished(sourceGone, pileFull, freeBefore, pile.calcFreeSpace());
            return changed;
        }
    }


    /**
     * Whether the inventory also holds other items of this item's stacking category. The batched
     * Stockpile.put() lets the server choose which items leave the inventory, so a mixed load has
     * to be moved one item at a time instead.
     *
     * When the caller pinned an exact name, resolve the check by exact name too - the NAlias-based
     * isSameExist() matches siblings by substring and so reports a collision for any item whose
     * name contains a sibling's name, even when the load is pure.
     */
    private boolean sameCategoryItemPresent(NGameUI gui) throws InterruptedException {
        if (exactName == null) {
            return StackSupporter.isSameExist(items, gui.getInventory());
        }
        return StackSupporter.isSameExistExact(exactName, gui.getInventory());
    }

    NAlias getStockpileName(NAlias items) {
        if (NParser.checkName(items.getDefault(), new NAlias("Soil"))) {
            return new NAlias("gfx/terobjs/stockpile-soil");
        } else if (NParser.checkName(items.getDefault(), new NAlias("board"))) {
            return new NAlias("gfx/terobjs/stockpile-board");
        } else if (NParser.checkName(items.getDefault(), new NAlias("Pumpkin Flesh"))) {
            return new NAlias("gfx/terobjs/stockpile-trash");
        } else if (NParser.checkName(items.getDefault(), new NAlias("pumpkin"))) {
            return new NAlias("gfx/terobjs/stockpile-pumpkin");
        } else if (NParser.checkName(items.getDefault(), new NAlias("metal"))) {
            return new NAlias("gfx/terobjs/stockpile-metal");
        } else if (NParser.checkName(items.getDefault(), new NAlias("brick"))) {
            return new NAlias("gfx/terobjs/stockpile-brick");
        } else if (NParser.checkName(items.getDefault(), new NAlias("fresh leaf of pipeweed"))) {
            return new NAlias("gfx/terobjs/stockpile-pipeleaves");
        } else if (NParser.checkName(items.getDefault(), new NAlias("Hemp Cloth"))) {
            return new NAlias("gfx/terobjs/stockpile-cloth");
        } else if (NParser.checkName(items.getDefault(), new NAlias("Linen Cloth"))) {
            return new NAlias("gfx/terobjs/stockpile-cloth");
        } else if (NParser.checkName(items.getDefault(), new NAlias("coal"))) {
            return new NAlias("gfx/terobjs/stockpile-coal");
        } else if (NParser.checkName(items.getDefault(), new NAlias("onion"))) {
            return new NAlias("gfx/terobjs/stockpile-onion");
        } else if (NParser.checkName(items.getDefault(), new NAlias("bone"))) {
            return new NAlias("gfx/terobjs/stockpile-bone");
        } else if (NParser.checkName(items.getDefault(), new NAlias("Odd Tuber"))) {
            return new NAlias("gfx/terobjs/stockpile-oddtuber");
        } else
            return new NAlias("stockpile");
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
            double quality = item.quality != null ? item.quality : 1.0;
            boolean nameMatches = exactName == null || item.name().equals(exactName);
            if (nameMatches && TransferItems2.matchesQuality(
                    quality, Math.max(th, 1), maxQualityExclusive)) {
                exactMatches.add(witem);
            }
        }
        return exactMatches;
    }
}
