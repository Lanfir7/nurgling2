package nurgling.actions.bots;

import haven.GItem;
import haven.Coord;
import haven.Loader;
import haven.WItem;
import nurgling.NGItem;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NMapView;
import nurgling.NUI;
import nurgling.sessions.ThreadLocalUI;
import nurgling.tools.StackSupporter;
import nurgling.tools.VSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.json.JSONObject;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LpGathererTest {
    private NConfig prior;

    @BeforeEach void config() {
        prior = NConfig.current;
        if (NConfig.current == null) NConfig.current = new NConfig();
    }

    @AfterEach void restoreConfig() { NConfig.current = prior; }

    private static <T> T bare(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static WItem wrapped(GItem item) throws Exception {
        WItem wrapper = bare(WItem.class);
        Field field = WItem.class.getDeclaredField("item");
        field.setAccessible(true);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        unsafe.putObject(wrapper, unsafe.objectFieldOffset(field), item);
        return wrapper;
    }

    @Test
    void choosesOnlyAProductSpecificFruitPetalAndNeverAnUnrelatedPick() {
        assertEquals("Pick figs", LpGatherer.petalFor("gfx/terobjs/trees/figtree", "Fig",
                Arrays.asList("Pick leaf", "Pick apples", "Pick figs")));
        assertNull(LpGatherer.petalFor("gfx/terobjs/trees/figtree", "Fig",
                Arrays.asList("Pick leaf", "Pick apples", "Chop tree")));
        assertEquals("Make boards", LpGatherer.petalFor("gfx/terobjs/trees/figtreelog", "Board of Figwood",
                Arrays.asList("Chop into blocks", "Make boards")));
        assertEquals("Chip stone", LpGatherer.petalFor("gfx/terobjs/bumlings/granite", "Granite",
                Arrays.asList("Chip stone", "Destroy")));
    }

    @Test
    void borrowedToolNeverDisplacesForeignHandsOrCursor() throws Exception {
        GItem owned = bare(NGItem.class), other = bare(NGItem.class);
        WItem own = wrapped(owned), foreign = wrapped(other), cursor = wrapped(other);
        assertTrue(LpGatherer.canBorrowTool(null, null, null, null));
        assertTrue(LpGatherer.canBorrowTool(null, own, null, owned));
        assertFalse(LpGatherer.canBorrowTool(cursor, null, null, null));
        assertFalse(LpGatherer.canBorrowTool(null, foreign, null, owned));
        assertFalse(LpGatherer.canBorrowTool(null, own, foreign, owned));
    }

    @Test
    void actualRunStopsBeforeAnyActionWhenCursorOrPlacementIsOccupied() throws Exception {
        NUI previous = ThreadLocalUI.get();
        try {
            NUI ui = bare(NUI.class);
            RecordingGameUI gui = bare(RecordingGameUI.class);
            ui.gui = gui;
            ThreadLocalUI.set(ui);
            gui.map = bare(NMapView.class);
            WItem held = new WItem(new NGItem(() -> null));
            gui.vhand = held;
            assertFalse(new LpGatherer().run(gui).IsSuccess());
            assertSame(held, gui.vhand);
            assertEquals(1, gui.errors);
            gui.vhand = null;
            Loader.Future<?> foreign = bare(Loader.Future.class);
            gui.map.placing = (Loader.Future) foreign;
            assertFalse(new LpGatherer().run(gui).IsSuccess());
            assertSame(foreign, gui.map.placing);
            assertEquals(2, gui.errors);
        } finally {
            if (previous == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(previous);
        }
    }

    private static final class RecordingGameUI extends NGameUI {
        int errors;
        private RecordingGameUI() { super("", 0L, null, null); }
        @Override public void error(String message) { errors++; }
    }

    @Test
    void creditWindowWaitsForLateNamesAndNeverReinterpretsAnUncertainOutcomeAsSuccess() {
        LpGatherer.CreditWindow delayed = new LpGatherer.CreditWindow(0);
        assertNull(delayed.observe(0, false, true, 5, false));
        assertNull(delayed.observe(12_000, false, true, 5, false));
        assertEquals(LpGatherer.Outcome.DISCOVERED, delayed.observe(13_000, true, true, 5, false));
        // A product can merge into an existing stack without consuming a new inventory cell.
        LpGatherer.CreditWindow merged = new LpGatherer.CreditWindow(0);
        assertNull(merged.observe(0, false, true, 5, false));
        assertNull(merged.observe(5000, false, true, 5, false));
        assertEquals(LpGatherer.Outcome.DISCOVERED, merged.observe(6000, true, true, 5, false));

        LpGatherer.CreditWindow gone = new LpGatherer.CreditWindow(0);
        assertNull(gone.observe(0, false, true, 5, true));
        assertNull(gone.observe(1000, false, false, 5, false));
        assertNull(gone.observe(4000, false, false, 5, true));
        assertNull(gone.observe(8000, false, false, 5, true));
        assertEquals(LpGatherer.Outcome.DISCOVERED, gone.observe(8500, true, false, 5, true));
        LpGatherer.CreditWindow unconfirmed = new LpGatherer.CreditWindow(0);
        assertNull(unconfirmed.observe(1000, false, false, 5, false));
        assertEquals(LpGatherer.Outcome.GONE, unconfirmed.observe(9000, false, false, 5, false));

        LpGatherer.CreditWindow loadingName = new LpGatherer.CreditWindow(0);
        assertNull(loadingName.observe(0, false, true, 5, false));
        assertNull(loadingName.observe(2000, false, true, 4, true));
        assertNull(loadingName.observe(20_000, false, false, 4, true));
        assertEquals(LpGatherer.Outcome.NOT_CREDITED, loadingName.observe(60_000, false, false, 4, true));
        LpGatherer.CreditWindow fastReceipt = new LpGatherer.CreditWindow(0, 5);
        assertNull(fastReceipt.observe(0, false, true, 4, true));
        assertNull(fastReceipt.observe(20_000, false, false, 4, true));
        assertEquals(LpGatherer.Outcome.DISCOVERED, fastReceipt.observe(30_000, true, false, 4, true));

        LpGatherer.CreditWindow idle = new LpGatherer.CreditWindow(0);
        assertNull(idle.observe(0, false, true, 5, true));
        assertEquals(LpGatherer.Outcome.IDLE, idle.observe(8000, false, true, 5, true));
        for (LpGatherer.Outcome uncertain : Arrays.asList(LpGatherer.Outcome.GONE,
                LpGatherer.Outcome.NO_MENU, LpGatherer.Outcome.IDLE,
                LpGatherer.Outcome.NOT_CREDITED, LpGatherer.Outcome.TIMEOUT))
            assertNotNull(LpGatherer.abortReason(uncertain));
        assertNull(LpGatherer.abortReason(LpGatherer.Outcome.DISCOVERED));
        assertNull(LpGatherer.abortReason(LpGatherer.Outcome.NO_PETAL));
    }

    @Test
    void toolReturnRequiresReceiptOfSameItemInDestination() {
        GItem owned = new NGItem(() -> null), foreign = new NGItem(() -> null);
        NInventory destination = new NInventory(Coord.of(3, 3));
        WItem foreignWrapper = destination.add(new WItem(foreign), Coord.z);
        assertFalse(LpGatherer.toolReturnReceipt(owned, null, destination));
        WItem ownedWrapper = destination.add(new WItem(owned), Coord.of(1, 0));
        assertFalse(LpGatherer.toolReturnReceipt(owned, ownedWrapper, destination));
        assertTrue(LpGatherer.toolReturnReceipt(owned, null, destination));
        assertNotSame(foreignWrapper.item, ownedWrapper.item);
    }

    @Test
    void correctedLogProductsMatchBoardCatalogWithoutChangingStackRules() {
        assertTrue(VSpec.object.get("gfx/terobjs/trees/figtreelog").contains("Board of Figwood"));
        assertTrue(VSpec.object.get("gfx/terobjs/trees/planetreelog").contains("Board of Planewood"));
        assertFalse(VSpec.object.get("gfx/terobjs/trees/figtreelog").contains("Board of Fig"));
        assertFalse(VSpec.object.get("gfx/terobjs/trees/planetreelog").contains("Board of Plane"));
        assertTrue(VSpec.categories.get("Board").stream().anyMatch(o ->
                "Board of Figwood".equals(o.optString("name")) && "gfx/invobjs/board-figtree".equals(o.optString("static"))));
        assertTrue(VSpec.categories.get("Board").stream().anyMatch(o ->
                "Board of Planewood".equals(o.optString("name")) && "gfx/invobjs/board-planetree".equals(o.optString("static"))));
        assertEquals(1, StackSupporter.getFullStackSize("Board of Figwood"));
        assertEquals(1, StackSupporter.getFullStackSize("Board of Planewood"));
    }
}
