package nurgling.tools;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecStoneCatalogTest {
    @Test
    void quarryartzUsesItsLiveNameAndResourceInStoneCategories() {
        assertEntry("Stone");
        assertEntry("Non-ore Stone");
    }

    private static void assertEntry(String category) {
        ArrayList<JSONObject> entries = VSpec.categories.get(category);
        assertTrue(entries != null && entries.stream().anyMatch(entry ->
                        "Quarryartz".equals(entry.optString("name"))
                                && "gfx/invobjs/quarryquartz".equals(entry.optString("static"))),
                category);
    }
}
