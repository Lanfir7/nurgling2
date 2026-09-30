package nurgling.actions;

import haven.Coord;
import haven.GItem;
import haven.GOut;
import haven.GSprite;
import haven.Inventory;
import haven.Resource;
import haven.WItem;
import haven.Widget;
import haven.res.ui.stackinv.ItemStack;
import haven.res.ui.tt.cn.CustomName;
import nurgling.NGItem;
import nurgling.NConfig;
import nurgling.NInventory;
import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PouchContainerTransferWidgetsTest {
    static {
        NConfig.getGlobalInstance();
        Resource.local().add(new Resource.FileSource(Paths.get("resources", "compiled", "res")));
        try {
            java.net.URLClassLoader resources = new java.net.URLClassLoader(new java.net.URL[]{
                    Paths.get("bin", "builtin-res.jar").toUri().toURL(), Paths.get("bin", "hafen-res.jar").toUri().toURL()});
            Resource.local().add(name -> {
                java.io.InputStream stream = resources.getResourceAsStream("res/" + name + ".res");
                if(stream == null) throw new java.io.FileNotFoundException(name);
                return stream;
            });
        } catch(java.net.MalformedURLException e) { throw new AssertionError(e); }
    }

    @Test
    void looseItemInNestedInventoryResolvesToThatInventory() throws Exception {
        Widget root = new Widget();
        NInventory main = inventory();
        place(root, main);
        NInventory nested = pouch(main, root);
        WItem loose = hold(nested, named("Apple"));

        assertSame(nested, PouchContainerTransfer.sourceInventory(loose));
        assertTrue(PouchContainerTransfer.isNestedInventory(nested));
        assertFalse(PouchContainerTransfer.isNestedInventory(main));
    }

    @Test
    void stackLeafFollowsContentsOwnerWhenWindowIsOnTheUiRoot() throws Exception {
        Widget root = new Widget();
        NInventory main = inventory();
        place(root, main);
        NInventory nested = pouch(main, root);
        StackLeaf stack = stack(nested, root, "Apple");

        assertSame(root, stack.window.parent);
        assertSame(nested, PouchContainerTransfer.sourceInventory(stack.leaf));
        assertTrue(PouchContainerTransfer.isNestedInventory(nested));
    }

    @Test
    void stackInMainInventoryIsNotAPouch() throws Exception {
        Widget root = new Widget();
        NInventory main = inventory();
        place(root, main);
        StackLeaf stack = stack(main, root, "Apple");

        assertSame(root, stack.window.parent);
        assertSame(main, PouchContainerTransfer.sourceInventory(stack.leaf));
        assertFalse(PouchContainerTransfer.isNestedInventory(main));
    }

    @Test
    void brokenOwnerContentsLinkIsRejected() throws Exception {
        Widget root = new Widget();
        NInventory main = inventory();
        place(root, main);
        NInventory nested = pouch(main, root);
        ((GItem.ContentsWindow) nested.parent).cont.contents = new Widget();
        assertFalse(PouchContainerTransfer.isNestedInventory(nested));

        StackLeaf stack = stack(main, root, "Apple");
        stack.owner.contents = new ItemStack();
        assertNull(PouchContainerTransfer.sourceInventory(stack.leaf));

        StackLeaf missingOwner = stack(main, root, "Apple");
        set(missingOwner.window, "cont", null);
        assertNull(PouchContainerTransfer.sourceInventory(missingOwner.leaf));
    }

    @Test
    void unitsNamedCountsOnlyTheExplicitInventoryIncludingStackLeaves() throws Exception {
        Widget root = new Widget();
        NInventory destination = inventory();
        place(root, destination);
        hold(destination, named("Apple"));
        stack(destination, root, "Apple", "Apple");
        NInventory nested = pouch(destination, root);
        hold(nested, named("Apple"));
        hold(nested, named("Apple"));
        stack(nested, root, "Apple");

        NInventory elsewhere = inventory();
        place(root, elsewhere);
        hold(elsewhere, named("Apple"));
        stack(elsewhere, root, "Apple", "Apple");

        assertEquals(3, PouchContainerTransfer.unitsNamed(destination, "Apple"));
        assertEquals(3, PouchContainerTransfer.unitsNamed(nested, "Apple"));
        assertEquals(3, PouchContainerTransfer.unitsNamed(elsewhere, "Apple"));
    }

    @Test
    void unloadedItemNameYieldsUnknownCount() throws Exception {
        NInventory loose = inventory();
        hold(loose, named(null));
        assertEquals(-1, PouchContainerTransfer.unitsNamed(loose, "Apple"));

        NInventory stacked = inventory();
        stack(stacked, new Widget(), "Apple", null);
        assertEquals(-1, PouchContainerTransfer.unitsNamed(stacked, "Apple"));
    }

    @Test
    void confirmationStaysFalseOnTimeout() {
        int[] calls = {0};
        PouchContainerTransfer.Confirmation wait = new PouchContainerTransfer.Confirmation(() -> {
            calls[0]++;
            return false;
        });

        int steps = 0;
        boolean finished;
        do {
            finished = wait.baseCheck();
            steps++;
            assertTrue(steps <= PouchContainerTransfer.WAIT_TICKS + 1);
        } while (!finished);

        assertEquals(PouchContainerTransfer.WAIT_TICKS + 1, steps);
        assertEquals(PouchContainerTransfer.WAIT_TICKS, calls[0]);
        assertFalse(wait.reached);
    }

    @Test
    void confirmationSucceedsAfterDelayedChecks() {
        int[] seen = {0};
        PouchContainerTransfer.Confirmation wait = new PouchContainerTransfer.Confirmation(() -> ++seen[0] >= 4);

        assertFalse(wait.baseCheck());
        assertFalse(wait.baseCheck());
        assertFalse(wait.baseCheck());
        assertFalse(wait.reached);
        assertTrue(wait.baseCheck());
        assertTrue(wait.reached);
    }

    @Test
    void confirmationInstancesDoNotShareState() {
        PouchContainerTransfer.Confirmation expired = new PouchContainerTransfer.Confirmation(() -> false);
        PouchContainerTransfer.Confirmation pending = new PouchContainerTransfer.Confirmation(() -> false);
        for (int i = 0; i < PouchContainerTransfer.WAIT_TICKS + 1; i++)
            expired.baseCheck();
        PouchContainerTransfer.Confirmation ready = new PouchContainerTransfer.Confirmation(() -> true);

        assertFalse(expired.reached);
        assertFalse(pending.baseCheck());
        assertFalse(pending.reached);
        assertTrue(ready.baseCheck());
        assertTrue(ready.reached);
        assertFalse(pending.reached);
    }

    @Test
    void fullDestinationDoesNotCountAsArrival() {
        NInventory inv = new NInventory(new Coord(0, 0));
        TransferToContainer.BoundedDestinationWait wait =
                new TransferToContainer.BoundedDestinationWait(inv, (NAlias) null, 1);

        assertEquals(0, inv.calcFreeSpace());
        assertFalse(wait.check());

        int steps = 0;
        boolean finished;
        do {
            finished = wait.baseCheck();
            steps++;
            assertTrue(steps <= PouchContainerTransfer.DESTINATION_WAIT_TICKS + 1);
        } while (!finished);

        assertEquals(PouchContainerTransfer.DESTINATION_WAIT_TICKS + 1, steps);
        assertFalse(wait.reached());
        assertFalse(wait.criticalExit);
    }

    @Test
    void boundedDestinationWaitReportsDelayedArrival() throws Exception {
        NInventory inv = inventory();
        TransferToContainer.BoundedDestinationWait wait =
                new TransferToContainer.BoundedDestinationWait(inv, new NAlias("Apple"), 1);

        assertFalse(wait.baseCheck());
        assertFalse(wait.reached());
        hold(inv, named("Apple"));
        assertTrue(wait.baseCheck());
        assertTrue(wait.reached());
    }

    @Test
    void existingVolumeItemDoesNotConfirmAnotherDeliveredItem() throws Exception {
        NInventory inv = inventory();
        WItem existing = hold(inv, named("Apple"));
        existing.item.info = java.util.Collections.singletonList(
                new CustomName(existing.item, "1 kg Apple"));
        TransferToContainer.BoundedDestinationWait wait =
                new TransferToContainer.BoundedDestinationWait(inv, new NAlias("Apple"), 2);

        assertFalse(wait.check());
        assertFalse(wait.reached());
        hold(inv, named("Apple"));
        assertTrue(wait.check());
        assertTrue(wait.reached());
    }

    @Test
    void broadAliasUsesTheSameLeafCountForBaselineAndReceipt() throws Exception {
        NInventory inv = inventory();
        hold(inv, named("Apple"));
        hold(inv, named("Apple Pie"));
        NAlias alias = new NAlias("Apple");
        int before = TransferToContainer.BoundedDestinationWait.count(inv, alias);
        TransferToContainer.BoundedDestinationWait wait =
                new TransferToContainer.BoundedDestinationWait(inv, alias, before + 1);

        assertEquals(2, before);
        assertFalse(wait.check());
        hold(inv, named("Apple"));
        assertTrue(wait.check());
    }

    @Test
    void brokenStackLinkAndSameInventoryTransferNothing() throws Exception {
        Widget root = new Widget();
        NInventory main = inventory();
        place(root, main);
        StackLeaf broken = stack(main, root, "Apple");
        broken.owner.contents = new ItemStack();
        NInventory other = inventory();

        assertEquals(0, TransferToContainer.transfer(broken.leaf, other, 1));

        StackLeaf same = stack(main, root, "Apple");
        assertEquals(0, TransferToContainer.transfer(same.leaf, main, 1));
    }

    @Test
    void roomPlanUsesActualChestGridAndItemLocations() throws Exception {
        Widget root = new Widget();
        NInventory chest = new NInventory(new Coord(2, 2));
        place(root, chest);
        WItem first = hold(chest, named("Stone"));
        first.item.spr = new SizedSprite(1, 1);
        first.c = new Coord(1, 1);
        WItem second = hold(chest, named("Stone"));
        second.item.spr = new SizedSprite(1, 1);
        second.c = new Coord(Inventory.sqsz.x + 1, 1);
        WItem source = witem(named("Board"));
        source.item.spr = new SizedSprite(1, 2);

        InventoryShapeRoom.Plan plan = InventoryShapeRoom.planFor(chest, source);

        assertEquals(InventoryShapeRoom.Decision.SHIFT, plan.decision);
        assertEquals(0, plan.move.fromCol);
        assertEquals(0, plan.move.fromRow);
        assertEquals(1, plan.move.toCol);
        assertEquals(1, plan.move.toRow);
    }

    @Test
    void ordinaryTransferSkipsChestWhenFreeCellsCannotFitItsShape() throws Exception {
        Widget root = new Widget();
        NInventory source = inventory();
        place(root, source);
        WItem board = hold(source, named("Board"));
        board.item.spr = new SizedSprite(2, 2);
        NInventory chest = new NInventory(new Coord(2, 2));
        place(root, chest);
        WItem first = hold(chest, named("Stone"));
        first.item.spr = new SizedSprite(1, 1);
        first.c = new Coord(1, 1);
        WItem second = hold(chest, named("Stone"));
        second.item.spr = new SizedSprite(1, 1);
        second.c = new Coord(Inventory.sqsz.x + 1, 1);
        Method method = TransferToContainer.class.getDeclaredMethod("transferAttempt",
                WItem.class, NInventory.class, int.class, boolean.class);
        method.setAccessible(true);

        PouchContainerTransfer.Outcome attempt = (PouchContainerTransfer.Outcome)
                method.invoke(null, board, chest, 1, false);

        assertEquals(0, attempt.moved);
        assertFalse(attempt.failed);
        assertSame(source, board.item.parent);
    }

    private static final class SizedSprite extends GSprite {
        private final Coord size;
        SizedSprite(int width, int height) {
            super(null);
            size = new Coord(width * 32, height * 32);
        }
        public void draw(GOut g) {}
        public Coord sz() { return size; }
    }

    private static NInventory pouch(NInventory outer, Widget root) throws Exception {
        NInventory inventory = inventory();
        NGItem owner = named("Pouch");
        GItem.ContentsWindow window = window(owner, inventory);
        place(window, inventory);
        place(root, window);
        owner.contents = inventory;
        hold(outer, owner);
        return inventory;
    }

    private static StackLeaf stack(NInventory inventory, Widget root, String... leaves) throws Exception {
        NGItem owner = named("Apple");
        ItemStack items = new ItemStack();
        owner.contents = items;
        GItem.ContentsWindow window = window(owner, items);
        place(window, items);
        place(root, window);
        hold(inventory, owner);
        WItem leaf = null;
        for (String leafName : leaves) {
            NGItem item = named(leafName);
            leaf = witem(item);
            place(items, item);
            place(items, leaf);
        }
        StackLeaf result = new StackLeaf();
        result.owner = owner;
        result.leaf = leaf;
        result.window = window;
        return result;
    }

    private static GItem.ContentsWindow window(GItem owner, Widget contents) throws Exception {
        GItem.ContentsWindow window = (GItem.ContentsWindow) unsafe().allocateInstance(GItem.ContentsWindow.class);
        set(window, "cont", owner);
        set(window, "inv", contents);
        return window;
    }

    private static WItem hold(Widget parent, NGItem item) throws Exception {
        WItem widget = witem(item);
        place(parent, item);
        place(parent, widget);
        return widget;
    }

    private static void place(Widget parent, Widget child) {
        child.parent = parent;
        child.link();
    }

    private static NInventory inventory() {
        return new NInventory(new Coord(4, 4));
    }

    private static NGItem named(String name) throws Exception {
        NGItem item = (NGItem) unsafe().allocateInstance(NGItem.class);
        set(item, "name", name);
        item.quality = 1f;
        return item;
    }

    private static WItem witem(NGItem item) throws Exception {
        WItem widget = (WItem) unsafe().allocateInstance(WItem.class);
        set(widget, "item", item);
        return widget;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        Field field = null;
        while (type != null) {
            try {
                field = type.getDeclaredField(name);
                break;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        field.setAccessible(true);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class StackLeaf {
        NGItem owner;
        WItem leaf;
        GItem.ContentsWindow window;
    }
}
