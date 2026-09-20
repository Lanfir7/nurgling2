package nurgling.tools;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecNewCropsTest {
    @Test
    void watermelonRadishAndWhiteOnionAreInStorageCategories() {
        assertTrue(hasStatic("Tuber", "gfx/invobjs/radish"));
        assertTrue(hasStatic("Onion", "gfx/invobjs/whiteonion"));
        assertTrue(hasStatic("Crop Seeds", "gfx/invobjs/seed-radish"));
        assertTrue(hasStatic("Crop Seeds", "gfx/invobjs/seed-watermelon"));
        assertTrue(hasStatic("Crops - other", "gfx/invobjs/watermelonslice"));
        assertTrue(hasStatic("Crops - other", "gfx/invobjs/small/watermelon"));
    }

    @Test
    void strawberriesUseTheirActualPluralDisplayName() {
        assertTrue(hasName("Berry", "gfx/invobjs/herbs/strawberry", "Strawberries"));
        assertTrue(hasName("Fruit or Berry", "gfx/invobjs/herbs/strawberry", "Strawberries"));
        assertTrue(hasName("Berry", "gfx/invobjs/woodstrawberry", "Wood Strawberry"));
    }

    private static boolean hasStatic(String category, String resource) {
        ArrayList<JSONObject> entries = VSpec.categories.get(category);
        return entries != null && entries.stream()
                .anyMatch(entry -> resource.equals(entry.optString("static")));
    }

    private static boolean hasName(String category, String resource, String name) {
        ArrayList<JSONObject> entries = VSpec.categories.get(category);
        return entries != null && entries.stream().anyMatch(entry ->
                resource.equals(entry.optString("static")) && name.equals(entry.optString("name")));
    }
}
