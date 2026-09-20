package nurgling.tools;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecIceBearHideTest {
    @Test
    void iceBearHidesUseLiveResourcesAndHideStackRules() {
        assertEntry("Hide Fresh", "Fresh Ice Bear Hide", "gfx/invobjs/polarbearhide-blood");
        assertEntry("Prepared Animal Hide", "Ice Bear Hide", "gfx/invobjs/polarbearhide");
        assertEquals(4, StackSupporter.getFullStackSize("Fresh Ice Bear Hide"));
        assertEquals(4, StackSupporter.getFullStackSize("Ice Bear Hide"));
    }

    private static void assertEntry(String category, String name, String resource) {
        ArrayList<JSONObject> entries = VSpec.categories.get(category);
        assertTrue(entries != null && entries.stream().anyMatch(entry ->
                        name.equals(entry.optString("name")) && resource.equals(entry.optString("static"))),
                category + ": " + name);
    }
}
