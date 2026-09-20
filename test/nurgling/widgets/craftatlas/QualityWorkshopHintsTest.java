package nurgling.widgets.craftatlas;

import nurgling.craftatlas.quality.QualityWorkshopModel.Key;
import nurgling.db.dao.StorageItemDao.StorageItemData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QualityWorkshopHintsTest {
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
    private static StorageItemData row(String name, double quality) { return new StorageItemData("hash", name, quality, "", "box"); }
}
