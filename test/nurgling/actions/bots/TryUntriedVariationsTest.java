package nurgling.actions.bots;

import haven.CheckBox;
import haven.Coord;
import haven.GItem;
import haven.WItem;
import nurgling.NConfig;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.cookbook.GenericTypes;
import nurgling.cookbook.Variations;
import nurgling.db.dao.StorageItemDao;
import nurgling.widgets.NCraftWindow;
import nurgling.widgets.NMakewindow;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TryUntriedVariationsTest {
    private static final class FakeMakewindow extends NMakewindow {
        int refreshes;
        private FakeMakewindow() { super("Test pie"); }
        @Override public void refreshModeUi() { refreshes++; }
    }

    private NConfig prior;

    @BeforeEach void setup() {
        prior = NConfig.current;
        if(NConfig.current == null) NConfig.current = new NConfig();
    }

    @AfterEach void restore() { NConfig.current = prior; }

    private static <T> T bare(Class<T> type) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return type.cast(((Unsafe)f.get(null)).allocateInstance(type));
    }

    private static NMakewindow.Spec spec(NMakewindow mw, String name, int count) throws Exception {
        NMakewindow.Spec s = bare(NMakewindow.Spec.class);
        s.name = name;
        s.count = count;
        s.subIngredientZones = new java.util.HashMap<>();
        return s;
    }

    private static NGItem item(String name, int amount) {
        return new NGItem(() -> null) {
            @Override public String name() { return name; }
            @Override public <C extends haven.ItemInfo> C getInfo(Class<C> c) {
                return c == GItem.Amount.class && amount > 0
                        ? c.cast(new GItem.Amount(this, amount)) : null;
            }
        };
    }

    @Test
    void countableVillageRowsExcludeBulkButKeepIndividualSeeds() {
        StorageItemDao.StorageItemData bulk = new StorageItemDao.StorageItemData(
                "bulk", "Barley Seed", 0, "#bulk:25 seeds", "barrel", 1L);
        StorageItemDao.StorageItemData seed = new StorageItemDao.StorageItemData(
                "seed", "Barley Seed", 19, "(1, 1)", "cupboard", 1L);
        StorageItemDao.StorageItemData milk = new StorageItemDao.StorageItemData(
                "milk", "Cowsmilk", 15, "#bulk:12 l", "barrel", 1L);
        Map<String, List<StorageItemDao.StorageItemData>> result =
                TryUntriedVariations.countableRows(List.of(bulk, seed, milk));
        assertEquals(List.of(seed), result.get("Barley Seed"));
        assertFalse(result.containsKey("Cowsmilk"));
    }

    @Test
    void outputReceiptCountsMergedAmountsAndRejectsUnreadNames() throws Exception {
        NMakewindow mw = bare(NMakewindow.class);
        mw.outputs = List.of(spec(mw, "Carrot Pie", 1));
        NInventory inv = new NInventory(Coord.of(3, 3));
        WItem stack = inv.add(new WItem(item("Carrot Pie", 4)), Coord.z);
        inv.add(new WItem(item("Other Pie", 7)), Coord.of(1, 0));
        assertEquals(4, TryUntriedVariations.outputUnits(mw, inv.child));
        assertEquals(4, Craft.preloadedUnits(stack));
        assertEquals(1, Craft.preloadedUnits(new WItem(item("Carrot", 0))));
        inv.add(new WItem(item(null, 0)), Coord.of(2, 0));
        assertEquals(-1, TryUntriedVariations.outputUnits(mw, inv.child));
    }

    @Test
    void repeatedPreloadedInputsRequireTheirCombinedUnits() throws Exception {
        NMakewindow mw = bare(NMakewindow.class);
        List<NMakewindow.Spec> inputs = List.of(spec(mw, "Carrot", 2), spec(mw, "Carrot", 3),
                spec(mw, "Salt", 1));
        assertEquals(Map.of("Carrot", 5), Craft.neededPreloaded(inputs, Set.of("Carrot"), 1));
        assertEquals(Map.of("Carrot", 10), Craft.neededPreloaded(inputs, Set.of("Carrot"), 2));
        WItem stack = new WItem(item("Carrot", 4));
        WItem leaf = new WItem(item("Carrot", 0));
        assertFalse(Craft.sufficientPreloaded(List.of(stack), "Carrot", 5));
        assertTrue(Craft.sufficientPreloaded(List.of(stack, leaf), "Carrot", 5));
        assertFalse(Craft.sufficientPreloaded(List.of(stack, new WItem(item("Beet", 2))), "Carrot", 5));
    }

    @Test
    void protectedCraftRejectsReplacementWrongRecipeOrLostNoTransfer() throws Exception {
        NMakewindow original = bare(NMakewindow.class);
        original.rcpnm = "Carrot Pie";
        original.inputs = List.of(spec(original, "Carrot", 2));
        original.outputs = List.of(spec(original, "Carrot Pie", 1));
        original.noTransfer = bare(CheckBox.class);
        original.noTransfer.a = true;
        NGameUI gui = bare(NGameUI.class);
        gui.craftwnd = bare(NCraftWindow.class);
        gui.craftwnd.makeWidget = original;
        Craft regular = new Craft(original, 1);
        Craft protectedCraft = new Craft(original, 1).withConfirmedPreloaded(Map.of("Carrot", 2))
                .preserveConsumables();
        assertTrue(regular.protectedWindowReady(gui));
        assertTrue(protectedCraft.protectedWindowReady(gui));

        NMakewindow foreign = bare(NMakewindow.class);
        foreign.rcpnm = "Beet Pie";
        foreign.inputs = List.of(spec(foreign, "Beet", 2));
        gui.craftwnd.makeWidget = foreign;
        assertFalse(protectedCraft.protectedWindowReady(gui));
        assertTrue(regular.protectedWindowReady(gui));
        gui.craftwnd.makeWidget = original;
        original.rcpnm = "Beet Pie";
        assertFalse(protectedCraft.protectedWindowReady(gui));
        original.rcpnm = "Carrot Pie";
        original.noTransfer.a = false;
        assertFalse(protectedCraft.protectedWindowReady(gui));
        original.noTransfer.a = true;
        original.inputs.get(0).ing = original.new Ingredient(null, "Beet", false);
        assertFalse(protectedCraft.protectedWindowReady(gui));
        original.inputs.get(0).ing = null;
        original.inputs.get(0).useCategory = true;
        assertFalse(protectedCraft.protectedWindowReady(gui));
    }

    @Test
    void changedRecipeInSameWindowDoesNotRestoreOldFlagsOrSelections() throws Exception {
        NMakewindow mw = bare(NMakewindow.class);
        mw.rcpnm = "Carrot Pie";
        mw.inputs = List.of(spec(mw, "Carrot", 1));
        mw.outputs = List.of(spec(mw, "Carrot Pie", 1));
        mw.noTransfer = bare(CheckBox.class);
        mw.noTransfer.a = false;
        Class<?> state = Class.forName("nurgling.actions.bots.TryUntriedVariations$WindowState");
        Constructor<?> ctor = state.getDeclaredConstructor(NMakewindow.class);
        ctor.setAccessible(true);
        Object captured = ctor.newInstance(mw);
        Method captureApplied = state.getDeclaredMethod("captureApplied", NMakewindow.class);
        captureApplied.setAccessible(true);
        Method restoreIfOwned = state.getDeclaredMethod("restoreIfOwned", NMakewindow.class);
        restoreIfOwned.setAccessible(true);

        mw.autoMode = true;
        mw.noTransfer.a = true;
        mw.inputs.get(0).ing = mw.new Ingredient(null, "Carrot", false);
        captureApplied.invoke(captured, mw);

        mw.rcpnm = "Beet Pie";
        mw.inputs.get(0).ing = mw.new Ingredient(null, "Beet", false);
        assertEquals(false, restoreIfOwned.invoke(captured, mw));
        assertTrue(mw.autoMode);
        assertTrue(mw.noTransfer.a);
        assertEquals("Beet", mw.inputs.get(0).ing.name);
        mw.rcpnm = "Carrot Pie";
        assertEquals(false, restoreIfOwned.invoke(captured, mw));
        assertEquals("Beet", mw.inputs.get(0).ing.name);
        mw.inputs.get(0).ing = mw.new Ingredient(null, "Carrot", false);
        mw.inputs.get(0).useCategory = true;
        assertEquals(false, restoreIfOwned.invoke(captured, mw));
        assertTrue(mw.inputs.get(0).useCategory);
    }

    @Test
    void unchangedAppliedSelectionRestoresOriginalPlayerCraftSettings() throws Exception {
        FakeMakewindow mw = bare(FakeMakewindow.class);
        mw.rcpnm = "Carrot Pie";
        mw.inputs = List.of(spec(mw, "Carrot", 1));
        mw.outputs = List.of(spec(mw, "Carrot Pie", 1));
        mw.noTransfer = bare(CheckBox.class);
        mw.autoMode = false;
        mw.noTransfer.a = false;
        Class<?> state = Class.forName("nurgling.actions.bots.TryUntriedVariations$WindowState");
        Constructor<?> ctor = state.getDeclaredConstructor(NMakewindow.class);
        ctor.setAccessible(true);
        Object captured = ctor.newInstance(mw);
        Method captureApplied = state.getDeclaredMethod("captureApplied", NMakewindow.class);
        captureApplied.setAccessible(true);
        Method restoreIfOwned = state.getDeclaredMethod("restoreIfOwned", NMakewindow.class);
        restoreIfOwned.setAccessible(true);

        mw.autoMode = true;
        mw.noTransfer.a = true;
        mw.inputs.get(0).categories = true;
        mw.inputs.get(0).ing = mw.new Ingredient(null, "Carrot", false);
        captureApplied.invoke(captured, mw);

        assertEquals(true, restoreIfOwned.invoke(captured, mw));
        assertFalse(mw.autoMode);
        assertFalse(mw.noTransfer.a);
        assertFalse(mw.inputs.get(0).categories);
        assertNull(mw.inputs.get(0).ing);
        assertEquals(1, mw.refreshes);
    }

    private static Variations variations() throws Exception {
        return variations(GenericTypes.bundled());
    }

    private static Variations variations(GenericTypes types) throws Exception {
        Constructor<Variations> c = Variations.class.getDeclaredConstructor(
                java.util.Collection.class, List.class, GenericTypes.class);
        c.setAccessible(true);
        return c.newInstance(List.of(), List.of(), types);
    }

    private static Variations.Option option(String name) throws Exception {
        Constructor<Variations.Option> c = Variations.Option.class.getDeclaredConstructor(
                String.class, String.class, String.class, int.class, java.util.Collection.class);
        c.setAccessible(true);
        return c.newInstance(name, name, null, 0, List.of(name));
    }

    private static JSONObject ingredient(Variations variations, String input, Variations.Option choice)
            throws Exception {
        Method m = TryUntriedVariations.class.getDeclaredMethod("ingredient",
                Variations.class, String.class, Variations.Option.class);
        m.setAccessible(true);
        return (JSONObject)m.invoke(null, variations, input, choice);
    }

    @Test
    void sameIconMilkAndButterSelectionsKeepTheirExactKinds() throws Exception {
        Variations v = variations();
        Variations.Option cow = option("Cowsmilk"), goat = option("Goatsmilk");
        JSONObject cowMilk = ingredient(v, "Milk", cow);
        JSONObject goatMilk = ingredient(v, "Milk", goat);
        JSONObject cowButter = ingredient(v, "Butter", cow);
        JSONObject goatButter = ingredient(v, "Butter", goat);
        assertNotNull(cowMilk);
        assertNotNull(goatMilk);
        assertNotNull(cowButter);
        assertNotNull(goatButter);
        assertEquals("Cowsmilk", cowMilk.getString("name"));
        assertEquals("Goatsmilk", goatMilk.getString("name"));
        assertEquals("Butter (Cowsmilk)", cowButter.getString("name"));
        assertEquals("Butter (Goatsmilk)", goatButter.getString("name"));
        assertNotEquals(cowButter.getString("name"), goatButter.getString("name"));
    }

    @Test
    void equallyFittingCraftSlotsAreAmbiguousAndNeverPickedByIndex() throws Exception {
        Constructor<GenericTypes> typesCtor = GenericTypes.class.getDeclaredConstructor(Map.class);
        typesCtor.setAccessible(true);
        Variations v = variations(typesCtor.newInstance(Map.of("Milk", List.of("Cowsmilk"))));
        Constructor<Variations.Slot> slotCtor = Variations.Slot.class.getDeclaredConstructor(
                String.class, double.class, boolean.class, boolean.class, List.class, boolean.class);
        slotCtor.setAccessible(true);
        Variations.Slot slot = slotCtor.newInstance("Milk", 1.0, false, false,
                List.of(option("Cowsmilk")), false);
        Method specFor = TryUntriedVariations.class.getDeclaredMethod("specFor",
                Variations.class, NMakewindow.class, Variations.Slot.class);
        specFor.setAccessible(true);
        NMakewindow mw = bare(NMakewindow.class);
        NMakewindow.Spec first = spec(mw, "Milk", 1), second = spec(mw, "Milk", 1);
        mw.inputs = List.of(first);
        assertEquals(0, specFor.invoke(null, v, mw, slot));
        mw.inputs = List.of(first, second);
        assertEquals(-1, specFor.invoke(null, v, mw, slot));
    }

    @Test
    void noneAttemptRequiresBothOptionalRecipeSlotAndOptionalGameInput() throws Exception {
        Constructor<Variations.Slot> slotCtor = Variations.Slot.class.getDeclaredConstructor(
                String.class, double.class, boolean.class, boolean.class, List.class, boolean.class);
        slotCtor.setAccessible(true);
        NMakewindow mw = bare(NMakewindow.class);
        NMakewindow.Spec gameOptional = spec(mw, "Seasoning", 1);
        Field opt = NMakewindow.Spec.class.getDeclaredField("opt");
        opt.setAccessible(true);
        opt.setInt(gameOptional, 1);
        Variations.Slot optional = slotCtor.newInstance("Seasoning", 1.0, true, false,
                List.of(option("Salt")), false);
        Variations.Slot mandatory = slotCtor.newInstance("Seasoning", 1.0, false, false,
                List.of(option("Salt")), false);
        Variations.Slot alreadyTried = slotCtor.newInstance("Seasoning", 1.0, true, false,
                List.of(option("Salt")), true);
        assertTrue(TryUntriedVariations.canOmit(optional, gameOptional));
        assertFalse(TryUntriedVariations.canOmit(mandatory, gameOptional));
        assertFalse(TryUntriedVariations.canOmit(alreadyTried, gameOptional));
        opt.setInt(gameOptional, 2);
        assertFalse(TryUntriedVariations.canOmit(optional, gameOptional));
    }

    @Test
    void anExplicitOtherPickCannotSilentlyTurnIntoAutocraft() {
        assertFalse(TryUntriedVariations.mappedOther(-1, 0));
        assertFalse(TryUntriedVariations.mappedOther(0, 0));
        assertTrue(TryUntriedVariations.mappedOther(1, 0));
    }
}
