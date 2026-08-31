package nurgling.actions;

import haven.*;
import nurgling.ExtraInvGroupTransfer;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NISBox;
import nurgling.NUtils;
import nurgling.pf.NHitBoxD;
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

    boolean allCategoryItemsInCurrentArea = false;

    enum PileMode {
        GOB_SHIFT_BULK,
        TYPE_BULK,
        ONE_BY_ONE
    }

    static PileMode pileMode(int th, boolean mixedCategory) {
        return pileMode(th, mixedCategory, false);
    }

    static PileMode pileMode(int th, boolean mixedCategory,
                             boolean allCategoryItemsInCurrentArea) {
        if (th > 1) {
            return PileMode.ONE_BY_ONE;
        }
        if (mixedCategory && !allCategoryItemsInCurrentArea) {
            return PileMode.TYPE_BULK;
        }
        return PileMode.GOB_SHIFT_BULK;
    }

    static PileMode thresholdPileMode(boolean mixedCategory, boolean allExactItemsInBand,
                                      boolean allCategoryItemsInCurrentArea) {
        if (!allExactItemsInBand) {
            return PileMode.ONE_BY_ONE;
        }
        if (!mixedCategory || allCategoryItemsInCurrentArea) {
            return PileMode.GOB_SHIFT_BULK;
        }
        return PileMode.TYPE_BULK;
    }

    static PileMode pileMode(int th, Double maxQualityExclusive,
                             boolean mixedCategory, boolean allExactItemsInBand,
                             boolean allCategoryItemsInCurrentArea) {
        if (th > 1 || maxQualityExclusive != null) {
            return thresholdPileMode(mixedCategory, allExactItemsInBand,
                    allCategoryItemsInCurrentArea);
        }
        return pileMode(th, mixedCategory, allCategoryItemsInCurrentArea);
    }

    static boolean allQualitiesInBand(List<Double> qualities,
                                      double minInclusive, Double maxExclusive) {
        if (qualities == null || qualities.isEmpty()) {
            return false;
        }
        for (Double quality : qualities) {
            double normalized = quality != null ? quality : 1.0;
            if (!TransferItems2.matchesQuality(normalized, minInclusive, maxExclusive)) {
                return false;
            }
        }
        return true;
    }

    /** One-by-one transfers synchronize each item with WaitFreeHand. */
    static boolean useExactInventoryWait(PileMode mode) {
        return false;
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
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        String key = exactName;
        if (key == null) {
            for (ExtraInvGroupTransfer.Slot slot : slots) {
                if (slot != null && ExtraInvGroupTransfer.isLeftover(slot.stack, slot.stackSize)
                        && slot.name != null) {
                    key = slot.name;
                    break;
                }
            }
        }
        if (key == null) {
            return null;
        }
        List<ExtraInvGroupTransfer.Slot> leftovers = ExtraInvGroupTransfer.matchingLeftovers(
                slots, key, NInventory.Grouping.NONE);
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

    /** Window 30/30 (free 0) is full even if gob model attr is not yet 31. */
    static boolean stockpileIsFull(long modelAttribute, int freeSpace) {
        return modelAttribute == 31 || freeSpace == 0;
    }

    static boolean shouldLeaveOpenedPile(long modelAttribute, int freeSpace) {
        return stockpileIsFull(modelAttribute, freeSpace);
    }

    static boolean shouldCloseStockpileWhenLeaving(boolean windowOpen) {
        return windowOpen;
    }

    static boolean keepFillingOpenedPile(long modelAttribute, int freeSpace) {
        return !stockpileIsFull(modelAttribute, freeSpace);
    }

    /** Full pile, no items left, or unreadable free space — do not spin while attr != 31. */
    static boolean shouldStopFillingOpenedPile(long modelAttribute, int freeSpace, int matchingItems) {
        return matchingItems <= 0 || freeSpace < 0 || stockpileIsFull(modelAttribute, freeSpace);
    }

    static boolean canStartPileMaker(boolean stockpileWindowOpen) {
        return !stockpileWindowOpen;
    }

    static boolean shouldCloseStockpileBeforePileMaker(boolean stockpileWindowOpen) {
        return stockpileWindowOpen;
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
        this(out, exactName, th, maxQualityExclusive, false);
    }

    public TransferToPiles(Pair<Coord2d,Coord2d> out, String exactName,
                           int th, Double maxQualityExclusive,
                           boolean allCategoryItemsInCurrentArea) {
        this(out, exactName, th);
        this.maxQualityExclusive = maxQualityExclusive;
        this.allCategoryItemsInCurrentArea = allCategoryItemsInCurrentArea;
    }


    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> witems;
        NAlias pileName;
        if (!(witems = getMatchingItems(gui)).isEmpty() ) {
                Gob target = null;
                boolean availablePileApproachFailed = false;
                for (Gob gob : Finder.findGobs(out, pileName = getStockpileName(items))) {
                    while (gob.ngob.getModelAttribute() != 31 && PathFinder.isAvailable(gob)) {
                            target = gob;
                            if (!approachExistingPile(gui, target)) {
                                availablePileApproachFailed = true;
                                break;
                            }
                            if(NUtils.getGameUI().vhand!=null) {
                                NUtils.activateItem(target, false);
                                NUtils.addTask(new WaitFreeHand(80, false));
                            }
                            witems = getMatchingItems(gui);
                            int size = witems.size();
                            Results opened = new OpenTargetContainer("Stockpile", target, true).run(gui);
                            if (!opened.IsSuccess() || gui.getStockpile() == null) {
                                availablePileApproachFailed = true;
                                break;
                            }
                            int freeSpace = gui.getStockpile().getFreeSpace();
                            int target_size = Math.min(size, freeSpace);
                            if (shouldStopFillingOpenedPile(gob.ngob.getModelAttribute(), freeSpace, size)) {
                                if (shouldCloseStockpileWhenLeaving(gui.getStockpile() != null)) {
                                    new CloseTargetContainer("Stockpile").run(gui);
                                }
                                if (size <= 0) {
                                    return Results.SUCCESS();
                                }
                                break;
                            }
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

                boolean hasRemainingItems = !getMatchingItems(gui).isEmpty();
                if (!shouldCreateNewPile(hasRemainingItems, availablePileApproachFailed)) {
                    return hasRemainingItems ? Results.FAIL() : Results.SUCCESS();
                }
                while(!getMatchingItems(gui).isEmpty() && out!=null) {
                    if (shouldCloseStockpileBeforePileMaker(gui.getStockpile() != null)) {
                        new CloseTargetContainer("Stockpile").run(gui);
                    }
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
                        Results opened = new OpenTargetContainer("Stockpile", pile, true).run(gui);
                        if (!opened.IsSuccess() || gui.getStockpile() == null)
                            return Results.FAIL();
                        int freeSpace = gui.getStockpile().getFreeSpace();
                        int target_size = Math.min(size, freeSpace);
                        if (shouldStopFillingOpenedPile(pile.ngob.getModelAttribute(), freeSpace, size)) {
                            if (shouldCloseStockpileWhenLeaving(gui.getStockpile() != null)) {
                                new CloseTargetContainer("Stockpile").run(gui);
                            }
                            break;
                        }
                        if (target_size > 0) {
                            if (!transfer(gui, target_size)) {
                                return Results.FAIL();
                            }
                        }
                    }
                }
            }
        return Results.SUCCESS();
        }

    private boolean approachExistingPile(NGameUI gui, Gob target) throws InterruptedException {
        boolean reached = attemptExistingPileApproach(gui, target);
        if (reached) {
            return true;
        }
        PathFinder preview = new PathFinder(target);
        preview.construct(true);
        return recoverExistingPileApproach(preview.startWasBlocked(),
                () -> leavePileArea(gui),
                () -> attemptExistingPileApproach(gui, target));
    }

    static boolean recoverExistingPileApproach(boolean stillBlocked,
                                               PileMaker.MovementAttempt exit,
                                               PileMaker.MovementAttempt retry)
            throws InterruptedException {
        return stillBlocked && exit.run() && retry.run();
    }

    static boolean shouldCreateNewPile(boolean hasRemainingItems,
                                       boolean availablePileApproachFailed) {
        return hasRemainingItems && !availablePileApproachFailed;
    }

    static boolean shouldReplanExistingPileLeg(int completedReplans) {
        return completedReplans < 1;
    }

    private boolean attemptExistingPileApproach(NGameUI gui, Gob target)
            throws InterruptedException {
        PathFinder preview = new PathFinder(target);
        preview.construct(true);
        if (preview.startWasBlocked()) {
            Coord2d freeStart = PileMaker.freeStartTarget(preview);
            if (!PileMaker.exitBlockedStart(true, freeStart,
                    point -> new GoTo(point).run(gui).IsSuccess())) {
                return false;
            }
        }

        PathFinder path = new PathFinder(target) {
            private int completedReplans = 0;

            @Override
            protected boolean onLegFailed(NGameUI currentGui, Coord2d at) {
                return shouldReplanExistingPileLeg(completedReplans++);
            }
        };
        return path.run(gui).IsSuccess() && clearInteractionOverlap(gui, target);
    }

    private boolean clearInteractionOverlap(NGameUI gui, Gob target)
            throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null || player.rc == null || player.ngob == null
                || player.ngob.hitBox == null || target == null || target.ngob == null
                || target.ngob.hitBox == null) {
            return true;
        }

        Coord2d retreat = interactionRetreatPoint(
                new NHitBoxD(player), new NHitBoxD(target), 0.5);
        if (retreat == null || retreat.dist(player.rc) <= 0.001) {
            return true;
        }
        if (!new GoTo(retreat).run(gui).IsSuccess()) {
            return false;
        }

        player = NUtils.player();
        return player != null && player.ngob != null && player.ngob.hitBox != null
                && !new NHitBoxD(player).intersects(new NHitBoxD(target), false);
    }

    static Coord2d interactionRetreatPoint(NHitBoxD playerBox, NHitBoxD targetBox,
                                           double clearance) {
        if (playerBox == null || targetBox == null || playerBox.rc == null) {
            return playerBox == null ? null : playerBox.rc;
        }

        Coord2d playerUL = playerBox.getCircumscribedUL();
        Coord2d playerBR = playerBox.getCircumscribedBR();
        Coord2d targetUL = targetBox.getCircumscribedUL();
        Coord2d targetBR = targetBox.getCircumscribedBR();
        double margin = Math.max(0, clearance);
        boolean insideInteractionMargin = playerBR.x > targetUL.x - margin
                && playerUL.x < targetBR.x + margin
                && playerBR.y > targetUL.y - margin
                && playerUL.y < targetBR.y + margin;
        if (!insideInteractionMargin) {
            return playerBox.rc;
        }
        Coord2d[] shifts = {
                Coord2d.of(targetUL.x - margin - playerBR.x, 0),
                Coord2d.of(targetBR.x + margin - playerUL.x, 0),
                Coord2d.of(0, targetUL.y - margin - playerBR.y),
                Coord2d.of(0, targetBR.y + margin - playerUL.y)
        };
        Coord2d best = shifts[0];
        for (int i = 1; i < shifts.length; i++) {
            if (shifts[i].abs() < best.abs()) {
                best = shifts[i];
            }
        }
        return playerBox.rc.add(best);
    }

    private boolean leavePileArea(NGameUI gui) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null || player.rc == null || out == null) {
            return false;
        }
        double minX = Math.min(out.a.x, out.b.x);
        double maxX = Math.max(out.a.x, out.b.x);
        double minY = Math.min(out.a.y, out.b.y);
        double maxY = Math.max(out.a.y, out.b.y);
        if (player.rc.x < minX || player.rc.x > maxX
                || player.rc.y < minY || player.rc.y > maxY) {
            return true;
        }

        List<Coord2d> exits = Finder.orderCandidatesNearestFirst(
                PileMaker.escapeTargets(out, MCache.tilesz.x, MCache.tilesz.x * 2),
                player.rc);
        for (Coord2d exit : exits) {
            if (PileMaker.nonRetryingPathFinder(exit).run(gui).IsSuccess()) {
                return true;
            }
        }
        return false;
    }

    private boolean transfer(NGameUI gui, int target_size) throws InterruptedException {
        NUtils.addTask(new WaitStockpile(true, 80, false));
        boolean mixedCategory = sameCategoryItemPresent(gui);
        boolean thresholdBand = th > 1 || maxQualityExclusive != null;
        PileMode mode = pileMode(th, maxQualityExclusive, mixedCategory,
                thresholdBand && allExactItemsInCurrentBand(gui),
                allCategoryItemsInCurrentArea);
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
            NISBox pile = gui.getStockpile();
            pile.beginDepositTracking();
            pile.wdgmsg("drop");
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
            pile.beginDepositTracking();
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

    private WItem findLeftover(ArrayList<WItem> matching) {
        ExtraInvGroupTransfer.Slot target = leftoverFlushTarget(slotsOf(matching), exactName);
        if (target == null) {
            return null;
        }
        for (WItem w : matching) {
            ExtraInvGroupTransfer.Slot slot = slotOf(w);
            if (slot != null && target.name.equals(slot.name)
                    && ExtraInvGroupTransfer.isLeftover(slot.stack, slot.stackSize)) {
                return w;
            }
        }
        return null;
    }

    private int countLeftovers(ArrayList<WItem> matching) {
        ExtraInvGroupTransfer.Slot target = leftoverFlushTarget(slotsOf(matching), exactName);
        if (target == null) {
            return 0;
        }
        return ExtraInvGroupTransfer.matchingLeftovers(
                slotsOf(matching), target.name, NInventory.Grouping.NONE).size();
    }

    private static ExtraInvGroupTransfer.Slot slotOf(WItem w) {
        if (w == null || !(w.item instanceof NGItem)) {
            return null;
        }
        String name = ((NGItem) w.item).name();
        if (name == null) {
            return null;
        }
        return NInventory.isLeftoverItem(w)
                ? ExtraInvGroupTransfer.Slot.solo(name, null)
                : ExtraInvGroupTransfer.Slot.stack(name, null);
    }

    private static List<ExtraInvGroupTransfer.Slot> slotsOf(ArrayList<WItem> matching) {
        ArrayList<ExtraInvGroupTransfer.Slot> slots = new ArrayList<>();
        if (matching == null) {
            return slots;
        }
        for (WItem w : matching) {
            ExtraInvGroupTransfer.Slot slot = slotOf(w);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return slots;
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
        pile.beginDepositTracking();
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

    private boolean allExactItemsInCurrentBand(NGameUI gui) throws InterruptedException {
        if (exactName == null) {
            return false;
        }
        ArrayList<Double> qualities = new ArrayList<>();
        for (WItem witem : gui.getInventory().getItems(items)) {
            NGItem item = (NGItem) witem.item;
            if (item.name().equals(exactName)) {
                qualities.add(item.quality != null ? item.quality : 1.0);
            }
        }
        return allQualitiesInBand(qualities, Math.max(th, 1), maxQualityExclusive);
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
