package nurgling.actions;

import haven.*;
import haven.res.ui.stackinv.ItemStack;
import haven.res.ui.tt.stackn.Stack;
import nurgling.*;
import nurgling.sessions.BotExecutor;
import nurgling.tasks.*;
import nurgling.tools.StackSupporter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sorts inventory items by name, resource name, and quality.
 * Moves all 1x1 items to fill empty slots from top-left, sorted alphabetically and by quality.
 */
public class SortInventory implements Action {
    
    public static final String[] EXCLUDE_WINDOWS = new String[]{
        "Character Sheet",
        "Study",
        "Chicken Coop",
        "Belt",
        "Pouch",
        "Purse",
        "Cauldron",
        "Finery Forge",
        "Fireplace",
        "Frame",
        "Herbalist Table",
        "Kiln",
        "Ore Smelter",
        "Smith's Smelter",
        "Oven",
        "Pane mold",
        "Rack",
        "Smoke shed",
        "Stack Furnace",
        "Steelbox",
        "Tub"
    };

    /**
     * "Study" is the character study window and must not match "Study Desk".
     */
    public static boolean isExcludedWindow(String caption) {
        if (caption == null || caption.isEmpty()) {
            return false;
        }
        if (caption.equals("Character Sheet") || caption.equals(nurgling.i18n.L10n.get("char.window_title"))) {
            return true;
        }
        for (String excluded : EXCLUDE_WINDOWS) {
            if ("Study".equals(excluded)) {
                if (caption.equals("Study")) {
                    return true;
                }
                continue;
            }
            if (caption.contains(excluded)) {
                return true;
            }
        }
        return false;
    }
    
    public static final Comparator<WItem> ITEM_COMPARATOR = (a, b) -> {
        // Both items must be NGItem
        if (!(a.item instanceof NGItem) || !(b.item instanceof NGItem)) {
            return 0;
        }

        NGItem itemA = (NGItem) a.item;
        NGItem itemB = (NGItem) b.item;

        // Compare by name first
        String nameA = itemA.name();
        String nameB = itemB.name();

        if (nameA == null) nameA = "";
        if (nameB == null) nameB = "";
        int nameCompare = nameA.compareTo(nameB);
        if (nameCompare != 0) return nameCompare;

        String resA = itemA.res.toString();
        String resB = itemB.res.toString();

        if (resA == null) resA = "";
        if (resB == null) resB = "";

        int resCompare = resA.compareTo(resB);
        if (resCompare != 0) return resCompare;

        // Then by quality (higher quality first)
        // Use stack quality if available, otherwise use item quality
        double qualA = getEffectiveQuality(itemA);
        double qualB = getEffectiveQuality(itemB);
        if (Double.compare(qualB, qualA) != 0) return Double.compare(qualB, qualA);

        int cA;
        GItem.Amount CntA = itemA.getInfo(GItem.Amount.class);
        if (CntA != null && CntA.itemnum() > 0) {
            cA = CntA.itemnum();
        } else {
            cA = 0;
        }

        int cB;
        GItem.Amount CntB = itemB.getInfo(GItem.Amount.class);
        if (CntB != null && CntB.itemnum() > 0) {
            cB = CntB.itemnum();
        } else {
            cB = 0;
        }

        if (cB != cA) return (cB - cA);

        return 0;
    };


    /**
     * Get effective quality for an item, considering stack quality for stacked items
     */
    private static double getEffectiveQuality(NGItem item) {
        // First try to get stack quality (for stacked items)
        Stack stackInfo = item.getInfo(Stack.class);
        if (stackInfo != null && stackInfo.quality > 0) {
            return stackInfo.quality;
        }
        // Fall back to individual item quality
        if (item.quality != null && item.quality > 0) {
            return item.quality;
        }
        return -1; // No quality available
    }
    
    private final NInventory inventory;
    private final boolean deepSort;
    private volatile boolean cancelled = false;
    private static volatile SortInventory current;
    private static final Object lock = new Object();

    public SortInventory(NInventory inventory) {
        this(inventory, false);
    }

    public SortInventory(NInventory inventory, boolean deepSort) {
        this.inventory = inventory;
        this.deepSort = deepSort;
    }
    
    /**
     * Check if cursor is default (not holding anything or special cursor)
     */
    private boolean isDefaultCursor(NGameUI gui) {
        return gui.vhand == null;
    }
    
    /**
     * Get item size in inventory cells
     */
    private Coord getItemSize(WItem item) {
        if (item.item.spr != null) {
            return item.item.spr.sz().div(UI.scale(32));
        }
        return new Coord(1, 1);
    }
    
    /**
     * Get item position in inventory grid
     */
    private Coord getItemPos(WItem item) {
        return item.c.sub(1, 1).div(Inventory.sqsz);
    }
    
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        // Check for default cursor
        if (!isDefaultCursor(gui)) {
            gui.error("Need default cursor to sort inventory!");
            return Results.FAIL();
        }
        
        // Cancel any previous sort operation
        cancel();
        synchronized (lock) {
            current = this;
        }
        
        try {
            sortLayout(gui);
            if (!cancelled && deepSort) {
                sortWithinStacks(gui);
            }
        } finally {
            synchronized (lock) {
                if (current == this) {
                    current = null;
                }
            }
        }
        
        if (!cancelled) {
            gui.msg(deepSort ? "Stacks sorted!" : "Inventory sorted!");
        }
        
        return cancelled ? Results.FAIL() : Results.SUCCESS();
    }
    
    private void sortLayout(NGameUI gui) throws InterruptedException {
        // Build grid of blocked cells (including sqmask and multi-cell items)
        boolean[][] grid = new boolean[inventory.isz.x][inventory.isz.y];
        
        // Apply sqmask if present
        boolean[] mask = inventory.sqmask;
        if (mask != null) {
            int mo = 0;
            for (int y = 0; y < inventory.isz.y; y++) {
                for (int x = 0; x < inventory.isz.x; x++) {
                    grid[x][y] = mask[mo++];
                }
            }
        }
        
        // Collect all items and mark multi-cell items as blocked
        List<WItem> items = new ArrayList<>();
        for (Widget wdg = inventory.lchild; wdg != null; wdg = wdg.prev) {
            if (cancelled) return;
            
            if (wdg.visible && wdg instanceof WItem) {
                WItem wItem = (WItem) wdg;
                Coord sz = getItemSize(wItem);
                Coord loc = getItemPos(wItem);
                
                if (sz.x * sz.y == 1) {
                    // 1x1 items can be sorted
                    items.add(wItem);
                } else {
                    // Multi-cell items stay in place, mark cells as blocked
                    for (int x = 0; x < sz.x; x++) {
                        for (int y = 0; y < sz.y; y++) {
                            int gx = loc.x + x;
                            int gy = loc.y + y;
                            if (gx >= 0 && gx < inventory.isz.x && gy >= 0 && gy < inventory.isz.y) {
                                grid[gx][gy] = true;
                            }
                        }
                    }
                }
            }
        }
        
        if (items.isEmpty()) {
            return;
        }
        


        // Sort items and create position mapping
        List<Object[]> sorted = items.stream()
                .filter(witem -> getItemSize(witem).x * getItemSize(witem).y == 1)
                .sorted(Comparator.comparing(witem -> getItemPos(witem), Comparator.reverseOrder()))
                .sorted(ITEM_COMPARATOR)
                .map(witem -> new Object[]{
                        witem,
                        getItemPos(witem),  // current pos
                        new Coord(0, 0)     // target pos (will be filled)
                })
                .collect(Collectors.toList());

        // Assign target positions
        int cur_x = -1, cur_y = 0;
        for (Object[] a : sorted) {
            if (cancelled) return;
            
            while (true) {
                cur_x += 1;
                if (cur_x == inventory.isz.x) {
                    cur_x = 0;
                    cur_y += 1;
                    if (cur_y == inventory.isz.y) break;
                }
                if (!grid[cur_x][cur_y]) {
                    a[2] = new Coord(cur_x, cur_y);
                    break;
                }
            }
            if (cur_y == inventory.isz.y) break;
        }
        
        // Move items to their target positions
        for (Object[] a : sorted) {
            if (cancelled) return;
            
            Coord currentPos = (Coord) a[1];
            Coord targetPos = (Coord) a[2];
            
            // Skip if already in right place
            if (currentPos.equals(targetPos)) {
                continue;
            }
            
            WItem wItem = (WItem) a[0];
            
            // Check if item is still valid
            if (wItem.item == null) {
                continue;
            }
            
            // Take item to hand
            NUtils.takeItemToHand(wItem);
            
            Object[] handu = a;
            while (handu != null) {
                if (cancelled) {
                    // Drop item back if cancelled
                    if (gui.vhand != null) {
                        NUtils.dropToInv(inventory);
                    }
                    return;
                }
                
                Coord dropPos = (Coord) handu[2];
                
                // Drop item at target position
                inventory.wdgmsg("drop", dropPos);
                
                // Find item that was at the target position (it's now in hand)
                Object[] b = null;
                for (Object[] x : sorted) {
                    if (((Coord) x[1]).equals(dropPos)) {
                        b = x;
                        break;
                    }
                }
                
                // Update current position
                handu[1] = handu[2];
                handu = b;
            }
            
            // Wait for hand to be free after chain is complete
            if (gui.vhand != null) {
                NUtils.getUI().core.addTask(new WaitFreeHand());
            }
        }
    }
    
    /**
     * Cancel the current sort operation
     */
    public static void cancel() {
        synchronized (lock) {
            if (current != null) {
                current.cancelled = true;
                current = null;
            }
        }
    }
    
    /**
     * Check if a sort operation is currently running
     */
    public static boolean isRunning() {
        synchronized (lock) {
            return current != null;
        }
    }
    
    /**
     * Sort a specific inventory (positional sort only)
     */
    public static void sort(NInventory inv) {
        if (!isValidInventory(inv)) {
            return;
        }

        NGameUI gui = NUtils.getGameUI();
        if (gui == null) return;

        if (gui.vhand != null) {
            gui.error("Need default cursor to sort inventory!");
            return;
        }

        BotExecutor.runAsync("InventorySorter", new SortInventory(inv));
    }

    /**
     * Deep sort: positional sort + redistribute items across same-type stacks
     * so highest quality items are concentrated in the first stacks.
     */
    public static void sortDeep(NInventory inv) {
        if (!isValidInventory(inv)) {
            return;
        }

        NGameUI gui = NUtils.getGameUI();
        if (gui == null) return;

        if (gui.vhand != null) {
            gui.error("Need default cursor to sort inventory!");
            return;
        }

        BotExecutor.runAsync("StackSorter", new SortInventory(inv, true));
    }
    
    /**
     * Check if inventory is valid for sorting (not in excluded windows)
     */
    private static boolean isValidInventory(NInventory inv) {
        if (inv == null) return false;

        Window wnd = inv.getparent(Window.class);
        if (wnd != null) {
            if (isExcludedWindow(wnd.cap)) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // Within-Stack Sorting (Second Pass) — Cycle-Chase Algorithm
    // =========================================================================

    private static class BufferLocation {
        final NInventory inv;
        final Coord coord;

        BufferLocation(NInventory inv, Coord coord) {
            this.inv = inv;
            this.coord = coord;
        }
    }

    /**
     * Sorts individual items across same-type stacks so that the highest
     * quality items are concentrated in the first stacks (descending).
     * Uses a cycle-chase permutation sort with a single-slot buffer.
     */
    private void sortWithinStacks(NGameUI gui) throws InterruptedException {
        // Wait a moment for the first-pass to fully settle in the UI
        NUtils.getUI().core.addTask(new WaitTicks(3));

        Set<String> names = new LinkedHashSet<>();
        for (Widget wdg = inventory.lchild; wdg != null; wdg = wdg.prev) {
            if (cancelled) return;
            if (!(wdg instanceof WItem)) continue;
            WItem w = (WItem) wdg;
            if (!(w.item instanceof NGItem)) continue;
            NGItem ng = (NGItem) w.item;
            if (ng.name() == null) continue;
            names.add(ng.name());
        }
        if (names.isEmpty()) return;

        boolean packed = false;
        for (String itemName : names) {
            if (cancelled) return;
            if (!StackSupporter.isKnownUnstackable(inventory, itemName)
                    && scanSlotCounts(itemName).size() >= 2) {
                packStacks(itemName);
                packed = true;
            }
        }

        if (packed && !cancelled) {
            NUtils.getUI().core.addTask(new WaitTicks(3));
            sortLayout(gui);
            NUtils.getUI().core.addTask(new WaitTicks(3));
        }

        Set<String> namesWithStacks = new HashSet<>();
        for (Widget wdg = inventory.lchild; wdg != null; wdg = wdg.prev) {
            if (cancelled) return;
            if (!(wdg instanceof WItem)) continue;
            WItem w = (WItem) wdg;
            if (!(w.item instanceof NGItem)) continue;
            NGItem ng = (NGItem) w.item;
            if (ng.name() != null && w.item.contents instanceof ItemStack) {
                namesWithStacks.add(ng.name());
            }
        }

        for (String itemName : namesWithStacks) {
            if (cancelled) return;

            Coord itemSize = getStackedItemSize(itemName);
            if (itemSize == null) continue;

            if (freshScan(itemName).size() < 2) {
                continue;
            }

            BufferLocation buffer = findBuffer(gui, itemSize);
            if (buffer == null) {
                gui.msg("Need 1 free " + itemSize.x + "x" + itemSize.y
                        + " slot to sort " + itemName + " stacks");
                continue;
            }
            performCycleSort(gui, itemName, buffer);
        }
    }

    static List<Integer> computePackedSlotSizes(int count, int maxStackSize) {
        List<Integer> sizes = new ArrayList<>();
        if (count <= 0) {
            return sizes;
        }
        int max = maxStackSize <= 1 ? 1 : maxStackSize;
        if (max == 1) {
            for (int i = 0; i < count; i++) {
                sizes.add(1);
            }
            return sizes;
        }
        while (count > 0) {
            int take = Math.min(max, count);
            sizes.add(take);
            count -= take;
        }
        return sizes;
    }

    static final int UNKNOWN_PACK_CAP = 10;

    static int packingMaxStackSize(int tableSize, int observedMax) {
        if (tableSize > 1) {
            return tableSize;
        }
        return UNKNOWN_PACK_CAP;
    }

    private void packStacks(String itemName) throws InterruptedException {
        int max = packingMaxStackSize(StackSupporter.getFullStackSize(itemName), 1);
        if (max <= 1) {
            return;
        }

        int cycles = 0;
        while (!cancelled && cycles++ < 500) {
            List<Object[]> slots = scanSlotCounts(itemName);
            if (slots.size() < 2) {
                return;
            }

            List<Coord> positions = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            int total = 0;
            for (Object[] entry : slots) {
                Coord pos = (Coord) entry[0];
                int c = (Integer) entry[1];
                positions.add(pos);
                counts.add(c);
                total += c;
            }
            if (total < 2) {
                return;
            }

            List<Integer> target = computePackedSlotSizes(total, max);
            if (counts.equals(target)) {
                return;
            }

            if (NUtils.getGameUI().vhand != null) {
                return;
            }

            int destIdx = -1;
            for (int i = 0; i < target.size() && i < counts.size(); i++) {
                if (counts.get(i) < target.get(i) && counts.get(i) < max) {
                    destIdx = i;
                    break;
                }
            }
            if (destIdx < 0) {
                for (int i = 0; i < counts.size() && i < target.size(); i++) {
                    if (counts.get(i) < max) {
                        destIdx = i;
                        break;
                    }
                }
            }
            if (destIdx < 0) {
                return;
            }

            int srcIdx = -1;
            for (int i = counts.size() - 1; i >= 0; i--) {
                if (i == destIdx || counts.get(i) <= 0) {
                    continue;
                }
                boolean extra = i >= target.size() || counts.get(i) > target.get(i);
                if (extra) {
                    srcIdx = i;
                    break;
                }
            }
            if (srcIdx < 0) {
                return;
            }

            takeAnyFromSlot(positions.get(srcIdx));
            if (NUtils.getGameUI().vhand == null) {
                return;
            }
            Coord destPos = positions.get(destIdx);
            Coord srcPos = positions.get(srcIdx);
            int destCountBefore = counts.get(destIdx);
            boolean stacked = addItemToSlotForPack(destPos);
            if (stacked) {
                continue;
            }
            if (NUtils.getGameUI().vhand != null) {
                inventory.wdgmsg("drop", srcPos);
                NUtils.addTask(new WaitFreeHand(200, false));
            }
            if (destCountBefore <= 1) {
                return;
            }
            max = destCountBefore;
        }
    }

    private boolean addItemToSlotForPack(Coord pos) throws InterruptedException {
        if (NUtils.getGameUI().vhand == null) {
            return true;
        }
        WItem slotItem = findSlotItemAtPos(pos);
        if (slotItem == null) {
            inventory.wdgmsg("drop", pos);
            NUtils.addTask(new WaitFreeHand(200, false));
            return NUtils.getGameUI().vhand == null;
        }
        ItemStack stack = slotItem.item.contents instanceof ItemStack
                ? (ItemStack) slotItem.item.contents : null;
        int oldSize = stack != null ? stack.wmap.size() : 0;
        NUtils.itemact(slotItem);
        NUtils.addTask(new WaitFreeHand(200, false));
        if (NUtils.getGameUI().vhand != null) {
            return false;
        }
        if (stack != null) {
            NUtils.addTask(new StackSizeChanged(stack, oldSize));
        }
        return true;
    }

    private List<Object[]> scanSlotCounts(String itemName) {
        List<Object[]> slots = new ArrayList<>();
        for (Widget wdg = inventory.lchild; wdg != null; wdg = wdg.prev) {
            if (!(wdg instanceof WItem)) continue;
            WItem w = (WItem) wdg;
            if (!(w.item instanceof NGItem)) continue;
            if (!itemName.equals(((NGItem) w.item).name())) continue;
            Coord pos = getItemPos(w);
            int count = getSlotCount(pos);
            if (count > 0) {
                slots.add(new Object[]{pos, count});
            }
        }
        slots.sort((a, b) -> {
            Coord pa = (Coord) a[0], pb = (Coord) b[0];
            return pa.y != pb.y ? Integer.compare(pa.y, pb.y) : Integer.compare(pa.x, pb.x);
        });
        return slots;
    }

    private int getSlotCount(Coord pos) {
        WItem slotItem = findSlotItemAtPos(pos);
        if (slotItem == null) {
            return 0;
        }
        if (slotItem.item.contents instanceof ItemStack) {
            return ((ItemStack) slotItem.item.contents).wmap.size();
        }
        if (slotItem.item instanceof NGItem) {
            return 1;
        }
        return 0;
    }

    private void takeAnyFromSlot(Coord pos) throws InterruptedException {
        WItem slotItem = findSlotItemAtPos(pos);
        if (slotItem == null) {
            return;
        }

        if (slotItem.item.contents instanceof ItemStack) {
            ItemStack stack = (ItemStack) slotItem.item.contents;
            int originalSize = stack.wmap.size();
            WItem target = null;
            for (GItem gi : stack.order) {
                target = stack.wmap.get(gi);
                if (target != null) {
                    break;
                }
            }
            if (target == null) {
                return;
            }
            NUtils.takeItemToHand(target);
            if (originalSize <= 2) {
                if (stack.parent != null) {
                    NUtils.addTask(new ISRemovedLoftar(
                            ((GItem.ContentsWindow) stack.parent).cont.wdgid(),
                            stack, originalSize));
                }
            } else {
                NUtils.addTask(new StackSizeChanged(stack, originalSize));
            }
        } else {
            if (slotItem.item.contents != null) {
                return;
            }
            int wdgid = slotItem.item.wdgid();
            NUtils.takeItemToHand(slotItem);
            NUtils.addTask(new ISRemoved(wdgid));
        }
    }

    private List<List<Float>> computeTargetState(List<Float> sortedQualities, List<Integer> slotSizes) {
        List<List<Float>> target = new ArrayList<>();
        int idx = 0;
        for (int size : slotSizes) {
            List<Float> slot = new ArrayList<>();
            for (int j = 0; j < size && idx < sortedQualities.size(); j++, idx++) {
                slot.add(sortedQualities.get(idx));
            }
            target.add(slot);
        }
        return target;
    }

    private boolean multisetEquals(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) return false;
        List<Float> bCopy = new ArrayList<>(b);
        for (float v : a) {
            int idx = findFloatIdx(bCopy, v);
            if (idx < 0) return false;
            bCopy.remove(idx);
        }
        return true;
    }

    /**
     * Scans the inventory fresh for all slots of the given item type.
     * Returns a list of (position, qualities) pairs, sorted by position.
     * Only includes slots that have at least one item with non-null quality.
     */
    private List<Object[]> freshScan(String itemName) {
        List<Object[]> slots = new ArrayList<>();
        for (Widget wdg = inventory.lchild; wdg != null; wdg = wdg.prev) {
            if (!(wdg instanceof WItem)) continue;
            WItem w = (WItem) wdg;
            if (!(w.item instanceof NGItem)) continue;
            NGItem ng = (NGItem) w.item;
            if (!itemName.equals(ng.name())) continue;

            Coord pos = getItemPos(w);
            List<Float> quals = getSlotQualities(pos);
            if (!quals.isEmpty()) {
                slots.add(new Object[]{pos, quals});
            }
        }
        // Sort by position (top-to-bottom, left-to-right) for stable ordering
        slots.sort((a, b) -> {
            Coord pa = (Coord) a[0], pb = (Coord) b[0];
            return pa.y != pb.y ? Integer.compare(pa.y, pb.y) : Integer.compare(pa.x, pb.x);
        });
        return slots;
    }

    /**
     * Cycle-chase sorting: repeatedly find misplaced items and resolve
     * permutation cycles using a single-slot buffer.
     *
     * Each cycle starts with a FRESH inventory scan so positions, sizes,
     * and qualities are never stale.
     */
    private void performCycleSort(NGameUI gui, String itemName,
            BufferLocation buffer) throws InterruptedException {

        int cycleNum = 0;
        boolean announced = false;
        while (!cancelled) {
            cycleNum++;

            // === Fresh scan each cycle ===
            List<Object[]> scan = freshScan(itemName);
            if (scan.size() < 2) break;

            List<Coord> positions = new ArrayList<>();
            List<List<Float>> current = new ArrayList<>();
            List<Integer> slotSizes = new ArrayList<>();
            List<Float> allQualities = new ArrayList<>();

            for (Object[] entry : scan) {
                Coord pos = (Coord) entry[0];
                @SuppressWarnings("unchecked")
                List<Float> quals = (List<Float>) entry[1];
                positions.add(pos);
                current.add(quals);
                slotSizes.add(quals.size());
                allQualities.addAll(quals);
            }

            if (allQualities.size() < 2) break;

            // Compute target: all qualities sorted descending, distributed by slot sizes
            List<Float> sortedQualities = new ArrayList<>(allQualities);
            sortedQualities.sort(Collections.reverseOrder());
            List<List<Float>> target = computeTargetState(sortedQualities, slotSizes);

            // Find a misplacement
            int fromSlot = -1;
            float excessQ = 0;
            int toSlot = -1;

            outer:
            for (int s = 0; s < current.size(); s++) {
                List<Float> excess = multisetDiff(current.get(s), target.get(s));
                for (float q : excess) {
                    for (int t = 0; t < target.size(); t++) {
                        if (t == s) continue;
                        List<Float> deficit = multisetDiff(target.get(t), current.get(t));
                        if (containsFloat(deficit, q)) {
                            fromSlot = s;
                            excessQ = q;
                            toSlot = t;
                            break outer;
                        }
                    }
                }
            }

            if (fromSlot < 0) {
                break;
            }

            // Safety: hand must be empty before starting a cycle
            if (gui.vhand != null) {
                gui.error("Stack sort failed: hand not empty. Drop held item and retry.");
                break;
            }

            if (!announced) {
                gui.msg("Sorting within " + itemName + " stacks...");
                announced = true;
            }

            // --- Execute one cycle ---

            // Step 1: take excess item → buffer
            takeItemFromSlot(positions.get(fromSlot), excessQ);
            // Verify we actually picked something up
            if (gui.vhand == null) {
                continue;
            }
            dropToBuffer(buffer);
            int bufferTarget = toSlot;
            int vacancy = fromSlot;

            // Step 2: chain — fill each vacancy from another slot
            // Use the target computed at cycle start (stable within this cycle)
            int chainStep = 0;
            while (bufferTarget != vacancy && !cancelled) {
                chainStep++;

                // Re-scan only the current state, keep target fixed
                List<List<Float>> chainCurrent = new ArrayList<>();
                for (Coord pos : positions) {
                    chainCurrent.add(getSlotQualities(pos));
                }

                List<Float> vacancyDeficit = multisetDiff(target.get(vacancy), chainCurrent.get(vacancy));

                float fillerQ = 0;
                int fillerSlot = -1;
                for (float needed : vacancyDeficit) {
                    for (int s = 0; s < chainCurrent.size(); s++) {
                        if (s == vacancy) continue;
                        List<Float> excess = multisetDiff(chainCurrent.get(s), target.get(s));
                        if (containsFloat(excess, needed)) {
                            fillerQ = needed;
                            fillerSlot = s;
                            break;
                        }
                    }
                    if (fillerSlot >= 0) break;
                }

                if (fillerSlot < 0) {
                    break;
                }

                takeItemFromSlot(positions.get(fillerSlot), fillerQ);
                if (gui.vhand == null) {
                    break;
                }
                addItemToSlot(positions.get(vacancy));
                vacancy = fillerSlot;
            }

            // Step 3: close cycle — buffer item → vacancy
            if (!cancelled) {
                retrieveFromBuffer(buffer);
                addItemToSlot(positions.get(vacancy));
            } else {
                if (gui.vhand == null) {
                    retrieveFromBuffer(buffer);
                }
                if (gui.vhand != null) {
                    NUtils.dropToInv(inventory);
                    NUtils.addTask(new WaitFreeHand());
                }
                return;
            }

            if (cycleNum > 500) {
                gui.msg("Stack sort: too many cycles, aborting");
                break;
            }
        }
    }

    // --- Buffer operations ---

    private BufferLocation findBuffer(NGameUI gui, Coord itemSize) throws InterruptedException {
        // Prefer a free area in the inventory being sorted
        Coord freeCoord = inventory.findFreeCoord(itemSize);
        if (freeCoord != null) {
            return new BufferLocation(inventory, freeCoord);
        }

        // Fall back to player inventory (when sorting a container)
        if (inventory != gui.maininv) {
            NInventory playerInv = gui.getInventory();
            if (playerInv != null) {
                Coord playerFree = playerInv.findFreeCoord(itemSize);
                if (playerFree != null) {
                    return new BufferLocation(playerInv, playerFree);
                }
            }
        }

        return null;
    }

    private void dropToBuffer(BufferLocation buffer) throws InterruptedException {
        if (NUtils.getGameUI().vhand == null) return;
        buffer.inv.wdgmsg("drop", buffer.coord);
        NUtils.addTask(new WaitFreeHand());
    }

    private void retrieveFromBuffer(BufferLocation buffer) throws InterruptedException {
        WItem item = findSlotItemAtPos(buffer.inv, buffer.coord);
        if (item != null) {
            NUtils.takeItemToHand(item);
        }
    }

    // --- Slot operations ---

    /**
     * Takes a specific item (identified by quality) from a slot to hand.
     * Handles stacks (2+), stacks dissolving (2→1), and single items.
     */
    private void takeItemFromSlot(Coord pos, float quality) throws InterruptedException {
        WItem slotItem = findSlotItemAtPos(pos);
        if (slotItem == null) return;

        if (slotItem.item.contents instanceof ItemStack) {
            ItemStack stack = (ItemStack) slotItem.item.contents;
            int originalSize = stack.wmap.size();

            WItem target = null;
            for (GItem gi : stack.order) {
                if (gi instanceof NGItem) {
                    NGItem ng = (NGItem) gi;
                    if (ng.quality != null && Math.abs(ng.quality - quality) < 0.001f) {
                        target = stack.wmap.get(gi);
                        break;
                    }
                }
            }
            if (target == null) return;

            NUtils.takeItemToHand(target);

            if (originalSize <= 2) {
                if (stack.parent != null) {
                    NUtils.addTask(new ISRemovedLoftar(
                            ((GItem.ContentsWindow) stack.parent).cont.wdgid(),
                            stack, originalSize));
                }
            } else {
                NUtils.addTask(new StackSizeChanged(stack, originalSize));
            }
        } else {
            // Safety: if the item has contents (some container/stack we don't recognize),
            // never take the whole thing — that would pick up an entire stack
            if (slotItem.item.contents != null) {
                return;
            }
            // Verify quality matches before taking a single item
            if (slotItem.item instanceof NGItem) {
                Float itemQ = ((NGItem) slotItem.item).quality;
                if (itemQ == null || Math.abs(itemQ - quality) >= 0.001f) {
                    return;
                }
            }
            int wdgid = slotItem.item.wdgid();
            NUtils.takeItemToHand(slotItem);
            NUtils.addTask(new ISRemoved(wdgid));
        }
    }

    /**
     * Adds the hand item to a slot. Handles empty slots, single items
     * (creates a stack), and existing stacks (grows the stack).
     */
    private void addItemToSlot(Coord pos) throws InterruptedException {
        if (NUtils.getGameUI().vhand == null) return;

        WItem slotItem = findSlotItemAtPos(pos);

        if (slotItem == null) {
            inventory.wdgmsg("drop", pos);
            NUtils.addTask(new WaitFreeHand());
        } else if (slotItem.item.contents instanceof ItemStack) {
            ItemStack stack = (ItemStack) slotItem.item.contents;
            int oldSize = stack.wmap.size();
            NUtils.itemact(slotItem);
            NUtils.addTask(new WaitFreeHand());
            NUtils.addTask(new StackSizeChanged(stack, oldSize));
        } else {
            NUtils.itemact(slotItem);
            NUtils.addTask(new WaitFreeHand());
        }
    }

    // --- Scan and lookup helpers ---

    /**
     * Returns the inventory cell size of items for the given item name
     * by finding any stack or single item of that name in the inventory.
     */
    private Coord getStackedItemSize(String itemName) {
        for (Widget wdg = inventory.lchild; wdg != null; wdg = wdg.prev) {
            if (!(wdg instanceof WItem)) continue;
            WItem w = (WItem) wdg;
            if (!(w.item instanceof NGItem)) continue;
            if (itemName.equals(((NGItem) w.item).name())) {
                return getItemSize(w);
            }
        }
        return null;
    }

    private List<Float> getSlotQualities(Coord pos) {
        WItem slotItem = findSlotItemAtPos(pos);
        if (slotItem == null) return new ArrayList<>();

        List<Float> qualities = new ArrayList<>();
        if (slotItem.item.contents instanceof ItemStack) {
            ItemStack stack = (ItemStack) slotItem.item.contents;
            for (GItem gi : stack.order) {
                if (gi instanceof NGItem && ((NGItem) gi).quality != null) {
                    qualities.add(((NGItem) gi).quality);
                }
            }
        } else if (slotItem.item instanceof NGItem) {
            NGItem ng = (NGItem) slotItem.item;
            if (ng.quality != null) {
                qualities.add(ng.quality);
            }
        }
        return qualities;
    }

    private WItem findSlotItemAtPos(Coord gridPos) {
        return findSlotItemAtPos(inventory, gridPos);
    }

    private static WItem findSlotItemAtPos(NInventory inv, Coord gridPos) {
        for (Widget wdg = inv.lchild; wdg != null; wdg = wdg.prev) {
            if (wdg instanceof WItem) {
                WItem w = (WItem) wdg;
                Coord pos = w.c.sub(1, 1).div(Inventory.sqsz);
                if (pos.equals(gridPos)) {
                    return w;
                }
            }
        }
        return null;
    }

    // --- Multiset utilities for quality comparison ---

    /**
     * Returns elements in {@code a} that are not matched in {@code b} (multiset difference).
     */
    private static List<Float> multisetDiff(List<Float> a, List<Float> b) {
        List<Float> bCopy = new ArrayList<>(b);
        List<Float> diff = new ArrayList<>();
        for (float v : a) {
            int idx = findFloatIdx(bCopy, v);
            if (idx >= 0) {
                bCopy.remove(idx);
            } else {
                diff.add(v);
            }
        }
        return diff;
    }

    private static boolean containsFloat(List<Float> list, float val) {
        return findFloatIdx(list, val) >= 0;
    }

    private static int findFloatIdx(List<Float> list, float val) {
        for (int i = 0; i < list.size(); i++) {
            if (Math.abs(list.get(i) - val) < 0.001f) {
                return i;
            }
        }
        return -1;
    }
}
