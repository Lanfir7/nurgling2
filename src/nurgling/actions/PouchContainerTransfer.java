package nurgling.actions;

import haven.Coord;
import haven.GItem;
import haven.Inventory;
import haven.Loading;
import haven.UI;
import haven.WItem;
import haven.Widget;
import haven.res.ui.stackinv.ItemStack;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NUtils;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitFreeHand;
import nurgling.tasks.WaitItemInHand;
import nurgling.tools.StackSupporter;
import java.util.function.BooleanSupplier;

/** Pouch transfers must name their destination: the server's transfer shortcut goes to the backpack. */
final class PouchContainerTransfer {
    static final int WAIT_TICKS = 200;
    static final int DESTINATION_WAIT_TICKS = 200;
    private PouchContainerTransfer() {}

    enum Place { NOT_FULL_STACK, SINGLE, FREE_SLOT }

    static final class Outcome {
        final boolean handled;
        final int moved;
        final boolean handStuck;
        final boolean failed;
        private Outcome(boolean handled, int moved, boolean handStuck, boolean failed) {
            this.handled = handled;
            this.moved = moved;
            this.handStuck = handStuck;
            this.failed = failed;
        }
        static Outcome skipped() { return new Outcome(false, 0, false, false); }
        static Outcome moved(int count) { return new Outcome(true, count, false, false); }
        static Outcome failed(boolean handStuck) { return new Outcome(true, 0, handStuck, true); }
    }

    interface Ops {
        boolean isPouchItem();
        boolean fromStack();
        boolean stackable();
        boolean handOccupied();
        boolean hasNotFullStack() throws InterruptedException;
        boolean hasSingle() throws InterruptedException;
        boolean targetHasFreeSpace() throws InterruptedException;
        default boolean roomPreparationFailed() { return false; }
        boolean takeToHand() throws InterruptedException;
        boolean place(Place place) throws InterruptedException;
        boolean destinationReceived() throws InterruptedException;
        boolean restoreToSourcePouch() throws InterruptedException;
        boolean mainInventoryHasRoom() throws InterruptedException;
        boolean restoreToMainInventory() throws InterruptedException;
    }

    static Place choose(boolean fromStack, boolean stackable, boolean partial, boolean single, boolean free) {
        if (stackable) {
            if (fromStack && single) return Place.SINGLE;
            if (partial) return Place.NOT_FULL_STACK;
            if (single) return Place.SINGLE;
        }
        return free ? Place.FREE_SLOT : null;
    }

    static Outcome moveOne(Ops ops, int limit) throws InterruptedException {
        if (!ops.isPouchItem()) return Outcome.skipped();
        if (limit <= 0) return Outcome.moved(0);
        // Never manipulate an item that was already in the hand before this operation.
        if (ops.handOccupied()) return Outcome.failed(true);
        // Inspect merge targets first; rearranging a chest is unnecessary for a merge.
        Place place = null;
        if (ops.stackable()) {
            if (ops.fromStack() && ops.hasSingle()) place = Place.SINGLE;
            else if (ops.hasNotFullStack()) place = Place.NOT_FULL_STACK;
            else if (ops.hasSingle()) place = Place.SINGLE;
        }
        if (place == null && ops.targetHasFreeSpace()) place = Place.FREE_SLOT;
        if (ops.roomPreparationFailed() || ops.handOccupied()) return Outcome.failed(ops.handOccupied());
        if (place == null) return Outcome.moved(0);
        if (!ops.takeToHand()) return recover(ops);
        if (ops.place(place) && ops.destinationReceived() && !ops.handOccupied())
            return Outcome.moved(1);
        return recover(ops);
    }

    private static Outcome recover(Ops ops) throws InterruptedException {
        if (ops.handOccupied()) {
            ops.restoreToSourcePouch();
            if (ops.handOccupied() && ops.mainInventoryHasRoom())
                ops.restoreToMainInventory();
        }
        // Recovery never counts as a delivery, even if the hand became empty.
        return Outcome.failed(ops.handOccupied());
    }

    static Ops live(WItem item, NInventory target, String name) {
        return new Live(item, target, name);
    }

    enum Room { READY, NO_ROOM, FAILED }

    /** Check the real destination shape before taking the source item. */
    static Room prepareRoom(WItem item, NInventory target) throws InterruptedException {
        InventoryShapeRoom.Plan plan = InventoryShapeRoom.planFor(target, item);
        if (plan.decision == InventoryShapeRoom.Decision.FITS) return Room.READY;
        if (plan.decision == InventoryShapeRoom.Decision.NO_ROOM) return Room.NO_ROOM;
        Live worker = new Live(item, target, "");
        return worker.shiftOne(plan.move) && target.findFreeCoord(item) != null
                ? Room.READY : Room.FAILED;
    }

    /** Follow the stack's owning GItem, not its window's position in the UI tree. */
    static NInventory sourceInventory(WItem item) {
        if (item == null || item.item == null) return null;
        Widget parent = item.item.parent;
        if (parent instanceof ItemStack) {
            if (!(parent.parent instanceof GItem.ContentsWindow)) return null;
            GItem owner = ((GItem.ContentsWindow) parent.parent).cont;
            if (owner == null || owner.contents != parent) return null;
            parent = owner.parent;
        }
        return parent instanceof NInventory ? (NInventory) parent : null;
    }

    static boolean isNestedInventory(NInventory inventory) {
        if (inventory == null || !(inventory.parent instanceof GItem.ContentsWindow)) return false;
        GItem owner = ((GItem.ContentsWindow) inventory.parent).cont;
        return owner != null && owner.contents == inventory;
    }

    /** Soft timeout with an explicit result; NTask completion alone also means timeout. */
    static final class Confirmation extends NTask {
        private final BooleanSupplier condition;
        boolean reached;
        Confirmation(BooleanSupplier condition) {
            this.condition = condition;
            infinite = false;
            criticalOnTimeout = false;
            maxCounter = WAIT_TICKS;
        }
        @Override public boolean check() {
            try {
                return reached = condition.getAsBoolean();
            } catch (Loading loading) {
                return false;
            }
        }
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        Confirmation wait = new Confirmation(condition);
        NUtils.addTask(wait);
        return wait.reached;
    }

    private static final class Live implements Ops {
        final WItem item;
        final NInventory source;
        final NInventory target;
        final String name;
        final NGameUI gui;
        int before;
        boolean roomPreparationFailed;

        Live(WItem item, NInventory target, String name) {
            this.item = item;
            this.target = target;
            this.name = name;
            this.source = sourceInventory(item);
            this.gui = NUtils.getGameUI();
        }
        public boolean isPouchItem() { return source != target && isNestedInventory(source); }
        public boolean fromStack() { return item.item.parent instanceof ItemStack; }
        public boolean stackable() { return StackSupporter.isStackable(target, name); }
        public boolean handOccupied() { return gui.vhand != null; }
        public boolean hasNotFullStack() { return stackable() && mergeTarget(target, true) != null; }
        public boolean hasSingle() { return stackable() && mergeTarget(target, false) != null; }
        public boolean targetHasFreeSpace() throws InterruptedException {
            Room room = prepareRoom(item, target);
            roomPreparationFailed = room == Room.FAILED;
            return room == Room.READY;
        }
        public boolean roomPreparationFailed() { return roomPreparationFailed; }

        private boolean shiftOne(InventoryShapeRoom.Move move) throws InterruptedException {
            WItem shifted = null;
            synchronized (gui.ui) {
                for (Widget child = target.child; child != null; child = child.next) {
                    if (!(child instanceof WItem)) continue;
                    WItem candidate = (WItem) child;
                    if (candidate.item.spr == null ||
                            !candidate.item.spr.sz().div(UI.scale(32)).equals(new Coord(1, 1))) continue;
                    Coord cell = candidate.c.div(Inventory.sqsz);
                    if (cell.x == move.fromCol && cell.y == move.fromRow) {
                        shifted = candidate;
                        break;
                    }
                }
            }
            if (shifted == null || handOccupied()) return false;
            WItem held = NUtils.takeItemToHand(shifted, WaitItemInHand.withSoftTimeout(shifted, WAIT_TICKS));
            if (held == null || !handOccupied()) {
                if (handOccupied()) restoreShift(move);
                return false;
            }
            target.wdgmsg("drop", new Coord(move.toCol, move.toRow));
            if (await(() -> {
                synchronized (gui.ui) {
                    short[][] grid = target.containerMatrix();
                    return !handOccupied() && grid != null
                            && grid[move.fromRow][move.fromCol] == 0
                            && grid[move.toRow][move.toCol] == 1
                            && target.findFreeCoord(item) != null;
                }
            })) return true;
            if (handOccupied()) restoreShift(move);
            return false;
        }
        private void restoreShift(InventoryShapeRoom.Move move) throws InterruptedException {
            target.wdgmsg("drop", new Coord(move.fromCol, move.fromRow));
            await(() -> !handOccupied());
        }

        public boolean takeToHand() throws InterruptedException {
            if (handOccupied()) return false;
            // Wait for loaded destination names BEFORE picking anything up.
            if (!await(() -> {
                synchronized (gui.ui) { return (before = unitsNamed(target, name)) >= 0; }
            })) return false;
            WItem hand = NUtils.takeItemToHand(item, WaitItemInHand.withSoftTimeout(item, WAIT_TICKS));
            return hand != null && name.equals(itemName(hand.item));
        }

        public boolean place(Place place) throws InterruptedException {
            if (!handOccupied() || !name.equals(itemName(gui.vhand.item))) return false;
            if (place == Place.FREE_SLOT) return drop(target);
            WItem merge = mergeTarget(target, place == Place.NOT_FULL_STACK);
            if (merge == null) return false;
            NUtils.itemact(merge);
            return true;
        }

        public boolean destinationReceived() throws InterruptedException {
            // Hand removal and destination item/stack updates can arrive on different ticks.
            return await(() -> {
                synchronized (gui.ui) {
                    return !handOccupied() && unitsNamed(target, name) > before;
                }
            });
        }

        public boolean restoreToSourcePouch() throws InterruptedException { return restore(source); }
        public boolean mainInventoryHasRoom() { return freeSlot(gui.getInventory(), gui.vhand) != null; }
        public boolean restoreToMainInventory() throws InterruptedException { return restore(gui.getInventory()); }

        private boolean restore(NInventory inventory) throws InterruptedException {
            if (!handOccupied()) return true;
            if (inventory == null || inventory.parent == null) return false;
            if (StackSupporter.isStackable(inventory, name)) {
                WItem merge = mergeTarget(inventory, true);
                if (merge == null) merge = mergeTarget(inventory, false);
                if (merge != null) {
                    NUtils.itemact(merge);
                    if (waitFreeHand()) return true;
                }
            }
            return drop(inventory) && waitFreeHand();
        }
        private boolean waitFreeHand() throws InterruptedException {
            NUtils.addTask(new WaitFreeHand(WAIT_TICKS, false));
            return !handOccupied();
        }
        private boolean drop(NInventory inventory) {
            Coord slot = freeSlot(inventory, gui.vhand);
            if (slot == null) return false;
            inventory.wdgmsg("drop", slot);
            return true;
        }
        private Coord freeSlot(NInventory inventory, WItem held) {
            if (inventory == null || inventory.parent == null || held == null || held.item.spr == null)
                return null;
            synchronized (gui.ui) { return inventory.findFreeCoord(held); }
        }
        private WItem mergeTarget(NInventory inventory, boolean stack) {
            synchronized (gui.ui) {
                for (Widget ch = inventory.child; ch != null; ch = ch.next) {
                    if (!(ch instanceof WItem)) continue;
                    WItem candidate = (WItem) ch;
                    if (!name.equals(itemName(candidate.item))) continue;
                    if (stack && candidate.item.contents instanceof ItemStack) {
                        int size = ((ItemStack) candidate.item.contents).wmap.size();
                        if (size > 0 && size < StackSupporter.getFullStackSize(name)) return candidate;
                    } else if (!stack && candidate.item.contents == null) {
                        return candidate;
                    }
                }
                return null;
            }
        }
    }

    /** Count only this inventory, including stack leaves, never augmented backpack/pouch listings. */
    static int unitsNamed(Widget inventory, String name) {
        int count = 0;
        for (Widget ch = inventory.child; ch != null; ch = ch.next) {
            if (!(ch instanceof WItem)) continue;
            GItem item = ((WItem) ch).item;
            String candidate = itemName(item);
            if (candidate == null) return -1;
            if (item.contents instanceof ItemStack) {
                int nested = unitsNamed(item.contents, name);
                if (nested < 0) return -1;
                count += nested;
            } else if (name.equals(candidate)) {
                // An unloaded stack is not a single item and cannot confirm receipt.
                if (!NGItem.validateItem((WItem) ch)) return -1;
                count++;
            }
        }
        return count;
    }

    private static String itemName(GItem item) {
        return item instanceof NGItem ? ((NGItem) item).name() : null;
    }
}
