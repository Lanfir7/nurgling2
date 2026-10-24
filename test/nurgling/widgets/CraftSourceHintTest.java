package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static nurgling.widgets.CraftSourceHint.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftSourceHintTest {
    private static CraftSourceHint.Selection selection(String spec, String picked, boolean category,
                                                       boolean useCategory, boolean ignored,
                                                       boolean selectedZone, boolean local,
                                                       boolean inventory, boolean subCraft) {
        return new CraftSourceHint.Selection(spec, picked, "Cheese room", ignored, category,
                useCategory, selectedZone, local, inventory, subCraft);
    }

    @Test void exactPickedMilkKindUsesItsOwnSourceAndExplicitChoicesWin() {
        CraftSourceHint.Selection milk = selection("Milk", "Sheepsmilk", true,
                false, false, false, false, false, false);
        assertEquals("Sheepsmilk", milk.effectiveName());
        assertEquals(AREA, CraftSourceHint.resolve(milk, "Sheep dairy", false, true, true,
                StorageCraftAvailability.Status.PRESENT).kind);
        assertEquals(STORAGE, CraftSourceHint.resolve(milk, null, false, true, true,
                StorageCraftAvailability.Status.PRESENT).kind);

        CraftSourceHint.Selection explicit = selection("Butter", "Butter (Sheepsmilk)", true,
                false, false, true, false, false, false);
        CraftSourceHint.Hint hint = CraftSourceHint.resolve(explicit, "Other area", false, true, true,
                StorageCraftAvailability.Status.PRESENT);
        assertEquals(AREA, hint.kind);
        assertEquals("Cheese room", hint.detail);

        assertEquals(INVENTORY, CraftSourceHint.resolve(selection("Butter", "Butter (Sheepsmilk)", true,
                false, false, false, false, true, false), null, false, true, true,
                StorageCraftAvailability.Status.PRESENT).kind);
        assertEquals(SUBCRAFT, CraftSourceHint.resolve(selection("Butter", "Butter (Sheepsmilk)", true,
                false, false, false, false, false, true), null, false, true, true,
                StorageCraftAvailability.Status.PRESENT).kind);
        assertEquals(LOCAL_ZONE, CraftSourceHint.resolve(selection("Butter", "Butter (Sheepsmilk)", true,
                false, false, false, true, false, false), null, false, true, true,
                StorageCraftAvailability.Status.PRESENT).kind);
    }

    @Test void unresolvedCategoryAndUnknownIndexNeverClaimMissingItems() {
        CraftSourceHint.Selection unresolved = selection("Milk", null, true,
                false, false, false, false, false, false);
        assertEquals(CATEGORY, CraftSourceHint.resolve(unresolved, null, false, true, true,
                StorageCraftAvailability.Status.EMPTY).kind);
        CraftSourceHint.Selection categoryMode = selection("Milk", "Sheepsmilk", true,
                true, false, false, false, false, false);
        assertEquals("Milk", categoryMode.effectiveName());
        assertEquals(CATEGORY, CraftSourceHint.resolve(categoryMode, null, false, true, true,
                StorageCraftAvailability.Status.EMPTY).kind);
        assertEquals(IGNORED, CraftSourceHint.resolve(selection("Milk", "Sheepsmilk", true,
                false, true, false, false, false, false), null, false, true, true,
                StorageCraftAvailability.Status.PRESENT).kind);

        CraftSourceHint.Selection barley = selection("Barley Seed", null, false,
                false, false, false, false, false, false);
        assertEquals(CHECKING, CraftSourceHint.resolve(barley, null, false, true, true,
                StorageCraftAvailability.Status.CHECKING).kind);
        assertEquals(UNAVAILABLE, CraftSourceHint.resolve(barley, null, false, true, true,
                StorageCraftAvailability.Status.UNAVAILABLE).kind);
        assertEquals(STORAGE_EMPTY, CraftSourceHint.resolve(barley, null, false, true, true,
                StorageCraftAvailability.Status.EMPTY).kind);
        assertEquals(NO_SOURCE, CraftSourceHint.resolve(barley, null, false, false, true,
                StorageCraftAvailability.Status.PRESENT).kind);
    }

    @Test void exactButterAreaPrecedesGenericFallbackAndWaterUsesSpecialisation() {
        String butter = "Butter (Sheepsmilk)";
        Map<String, String> near = new HashMap<>(), global = new HashMap<>();
        near.put("Butter", "generic TAKE");
        global.put(butter, "exact distant TAKE");
        assertEquals("exact distant TAKE", CraftSourceHint.inputArea(butter, near::get, global::get));
        global.clear();
        assertEquals("generic TAKE", CraftSourceHint.inputArea(butter, near::get, global::get));
        assertNull(CraftSourceHint.inputArea("Butter (unknown)", near::get, global::get));

        near.put("Water", "ordinary area");
        Map<String, String> nearSpecs = new HashMap<>(), globalSpecs = new HashMap<>();
        globalSpecs.put("water", "water area");
        String source = CraftSourceHint.waterArea("Water", near::get, global::get,
                "barrel area"::equals, nearSpecs::get, globalSpecs::get);
        assertEquals("water area", source);
        assertEquals(WATER_AREA, CraftSourceHint.resolve(selection("Water", null, false,
                false, false, false, false, false, false), source, true, false, false,
                null).kind);
        assertEquals(WATER_CHECKED_AT_CRAFT, CraftSourceHint.resolve(selection("Water", null, false,
                false, false, false, false, false, false), null, true, false, false,
                null).kind);
        near.put("Water", "barrel area");
        assertEquals("barrel area", CraftSourceHint.waterArea("Water", near::get, global::get,
                "barrel area"::equals, nearSpecs::get, globalSpecs::get));
    }

    @Test void tooltipContextInvalidatesBothDurationsForSourceAndNameChanges() {
        CraftSourceHint.TooltipCache<Object, String> cache = new CraftSourceHint.TooltipCache<>();
        Object first = new Object(), second = new Object();
        cache.context(first, Arrays.asList(true, "Sheep Milk", CHECKING));
        cache.shortTip("short-checking");
        cache.longTip("long-checking");
        cache.context(first, Arrays.asList(true, "Sheep Milk", CHECKING));
        assertEquals("short-checking", cache.shortTip());
        assertEquals("long-checking", cache.longTip());

        cache.context(first, Arrays.asList(true, "Sheep Milk", STORAGE));
        assertNull(cache.shortTip());
        assertNull(cache.longTip());
        cache.shortTip("short-storage");
        cache.longTip("long-storage");
        cache.context(first, Arrays.asList(true, "Cow Milk", STORAGE));
        assertNull(cache.shortTip());
        assertNull(cache.longTip());

        cache.shortTip("short-cow");
        cache.longTip("long-cow");
        cache.context(first, Arrays.asList(false, "Cow Milk", STORAGE));
        assertNull(cache.shortTip());
        assertNull(cache.longTip());
        cache.shortTip("manual");
        cache.context(second, Arrays.asList(false, "Cow Milk", STORAGE));
        assertNull(cache.shortTip());
    }
}
