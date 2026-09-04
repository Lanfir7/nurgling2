package nurgling.tools;

import nurgling.db.dao.StorageItemDao;
import nurgling.widgets.NStorageItemsWidget.GroupedItem;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftIngredientStockTest {

    @Test
    void namesForConcreteItem() {
        assertEquals(List.of("Nettle"), CraftIngredientStock.namesFor("Nettle", false, null));
    }

    @Test
    void namesForPickedMemberIgnoresCategory() {
        assertEquals(List.of("Barley"), CraftIngredientStock.namesFor("Cereals", true, "Barley"));
    }

    @Test
    void namesForCategoryExpandsVSpec() {
        String key = "CraftIngredientStockTestCat";
        ArrayList<JSONObject> members = new ArrayList<>();
        members.add(named("Wheat"));
        members.add(named("Barley"));
        VSpec.categories.put(key, members);
        try {
            List<String> names = CraftIngredientStock.namesFor(key, true, null);
            assertEquals(List.of("Wheat", "Barley"), names);
        } finally {
            VSpec.categories.remove(key);
        }
    }

    @Test
    void groupByQualitySplitsDifferentQualities() {
        List<StorageItemDao.StorageItemData> raw = List.of(
                item("ha", "Nettle", 10, "c1"),
                item("hb", "Nettle", 12, "c1")
        );
        List<GroupedItem> grouped = CraftIngredientStock.groupByQuality(raw, Map.of());
        assertEquals(2, grouped.size());
        assertEquals(12.0, grouped.get(0).quality, 0.01);
        assertEquals(10.0, grouped.get(1).quality, 0.01);
    }

    @Test
    void groupByQualityMergesSameQuality() {
        List<StorageItemDao.StorageItemData> raw = List.of(
                item("ha", "Nettle", 10.00, "c1"),
                item("hb", "Nettle", 10.001, "c1")
        );
        List<GroupedItem> grouped = CraftIngredientStock.groupByQuality(raw, Map.of());
        assertEquals(1, grouped.size());
        assertEquals(2, grouped.get(0).count);
        assertEquals("Nettle", grouped.get(0).name);
    }

    @Test
    void qualityGroupingDoesNotDependOnDecimalLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            List<StorageItemDao.StorageItemData> raw = List.of(
                    item("ha", "Nettle", 10.00, "c1"),
                    item("hb", "Nettle", 10.001, "c1"));
            assertEquals(1, CraftIngredientStock.groupByQuality(raw, Map.of()).size());
            assertEquals("10.00", CraftIngredientStock.qualityKey(10));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void totalsSumCountAndMaxQuality() {
        List<StorageItemDao.StorageItemData> raw = List.of(
                item("ha", "Wheat", 20, "c1"),
                item("hb", "Barley", 35, "c1"),
                item("hc", "Barley", 35, "c2")
        );
        List<GroupedItem> grouped = CraftIngredientStock.groupByQuality(raw, Map.of());
        CraftIngredientStock.Totals totals = CraftIngredientStock.totals(grouped);
        assertEquals(3, totals.count);
        assertTrue(totals.maxQuality >= 35);
    }

    @Test
    void emptyTotals() {
        CraftIngredientStock.Totals totals = CraftIngredientStock.totals(List.of());
        assertEquals(0, totals.count);
        assertEquals(0, totals.maxQuality, 0.001);
    }

    private static JSONObject named(String name) {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        return obj;
    }

    private static StorageItemDao.StorageItemData item(String hash, String name, double quality, String container) {
        return new StorageItemDao.StorageItemData(hash, name, quality, "(0,0)", container);
    }
}
