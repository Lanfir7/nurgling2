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

    private static boolean hasStatic(String category, String resource) {
        ArrayList<JSONObject> entries = VSpec.categories.get(category);
        return entries != null && entries.stream()
                .anyMatch(entry -> resource.equals(entry.optString("static")));
    }
}
