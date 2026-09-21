package nurgling.widgets.craftatlas;

import nurgling.craftatlas.quality.QualityWorkshopModel.Key;
import nurgling.db.dao.StorageItemDao.StorageItemData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QualityWorkshopHintsTest {
    @Test void miningAndFurnaceSuggestionsUseRealMaterialsWithoutGuessingNodeQuality() {
        assertTrue(QualityWorkshopCatalog.storageNames(Key.STONE_WALL).isEmpty());
        assertTrue(QualityWorkshopCatalog.storageNames(Key.ORE_WALL).isEmpty());
        assertTrue(QualityWorkshopCatalog.storageNames(Key.COAL).contains("Coal"));
        assertTrue(QualityWorkshopCatalog.storageNames(Key.COAL).contains("Black Coal"));
        assertFalse(QualityWorkshopCatalog.storageNames(Key.COAL).contains("Branch"));
        assertTrue(QualityWorkshopCatalog.storageNames(Key.HARD_METAL).contains("Bar of Cast Iron"));
        assertFalse(QualityWorkshopCatalog.storageNames(Key.HARD_METAL).contains("Bar of Copper"));
        assertEquals("station:smelter", QualityWorkshopCatalog.stationKey(Key.ORE_SMELTER, false));
        assertEquals("station:stack-furnace", QualityWorkshopCatalog.stationKey(Key.STACK_FURNACE, false));
        assertEquals("survive", QualityWorkshopCatalog.attribute(Key.SURVIVAL));
    }
    @Test void usesActualMaximumAndExactNamesAcrossMaterialCategories() {
        Map<Key, Set<String>> names = new EnumMap<>(Key.class);
        names.put(Key.BONES, new HashSet<>(Arrays.asList("Bone Material", "Whale Bone Material")));
        names.put(Key.BRICK, Collections.singleton("Brick"));
        List<StorageItemData> rows = Arrays.asList(row("Bone Material", 30), row("Bone Material", 99),
                row("Whale Bone Material", 140), row("Bone Glue", 999), row("Brick", 75),
                row("Brick", Double.NaN), row("Brick", Double.POSITIVE_INFINITY), row("Brick", -5));
        Map<Key, QualityWorkshopHints.Hint> result = QualityWorkshopHints.best(names, rows);
        assertEquals(140, result.get(Key.BONES).quality);
        assertEquals("Whale Bone Material", result.get(Key.BONES).name);
        assertEquals(75, result.get(Key.BRICK).quality);
        assertFalse(result.containsKey(Key.FUEL));
    }
    @Test void missingAndInvalidQualitiesDoNotCreateFakeSuggestions() {
        Map<Key, Set<String>> names = new EnumMap<>(Key.class);
        names.put(Key.BRICK, Collections.singleton("Brick"));
        assertTrue(QualityWorkshopHints.best(names, Arrays.asList(null, row("Brick", 0))).isEmpty());
    }
    @Test void cauldronHintsRespectSelectedMaterialAndDoNotConflateStations() {
        assertEquals("station:clay-cauldron", QualityWorkshopCatalog.stationKey(Key.CAULDRON, true));
        assertEquals("station:cauldron", QualityWorkshopCatalog.stationKey(Key.CAULDRON, false));
        assertEquals("station:potters-wheel", QualityWorkshopCatalog.stationKey(Key.POTTERS_WHEEL, false));
        assertNull(QualityWorkshopCatalog.stationKey(Key.BRICK, false));
    }
    @Test void castingMaterialSuggestionsIncludeClaysAndSand() {
        Set<String> names = QualityWorkshopCatalog.storageNames(Key.CASTING_MATERIAL);
        assertTrue(names.contains("Sand"));
        assertTrue(names.contains("Bad Sand"));
        assertTrue(names.contains("Bone Clay"));
        assertTrue(names.contains("Potter's Clay"));
        assertFalse(names.contains("Brick"));
        assertEquals("station:anvil", QualityWorkshopCatalog.stationKey(Key.ANVIL, false));
    }
    @Test void workshopUsesGameResourcesForCommonItemsMissingFromWikiIconSheets() {
        assertEquals("gfx/invobjs/rawglass", QualityWorkshopCatalog.iconResource(Key.RAW_GLASS));
        assertEquals("gfx/invobjs/clay-coade", QualityWorkshopCatalog.iconResource(Key.COADE_CLAY));
        assertEquals("gfx/invobjs/clay-potters", QualityWorkshopCatalog.iconResource(Key.POTTER_CLAY));
        assertEquals("gfx/invobjs/saltwater", QualityWorkshopCatalog.iconResource(Key.SALT_WATER));
        assertEquals("paginae/bld/potterswheel", QualityWorkshopCatalog.iconResource(Key.POTTERS_WHEEL));
    }
    private static StorageItemData row(String name, double quality) { return new StorageItemData("hash", name, quality, "", "box"); }
}
