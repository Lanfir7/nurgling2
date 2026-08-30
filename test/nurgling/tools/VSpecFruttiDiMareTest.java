package nurgling.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecFruttiDiMareTest {
    private static final List<String> WIKI_MEMBERS = List.of(
            "Bay Shrimp",
            "Gooseneck Barnacle",
            "Lake Snail",
            "Oyster",
            "Pearl Oyster",
            "Raw Crab",
            "Raw Lobster",
            "Razor Clam",
            "River Pearl Mussel",
            "Round Clam"
    );

    @Test
    void fruttiDiMareMatchesCurrentWikiMembersAndResourceSpecs() {
        ArrayList<JSONObject> entries = VSpec.categories.get("Frutti di Mare");
        assertNotNull(entries);

        List<String> names = entries.stream()
                .map(entry -> entry.getString("name"))
                .collect(Collectors.toList());
        assertEquals(WIKI_MEMBERS, names);

        NAlias category = VSpec.getNamesInCategory("Frutti di Mare");
        for (String name : WIKI_MEMBERS) {
            assertTrue(category.matches(name), name);
        }
        assertFalse(category.matches("Lobster"));
        assertFalse(category.matches("Forest Snail"));

        assertTrue(VSpec.categoriesFor(
                Collections.emptyList(),
                Collections.singletonList("gfx/invobjs/herbs/pearloyster")
        ).contains("Frutti di Mare"));

        assertLayers(entryNamed(entries, "Raw Crab"),
                "gfx/invobjs/meat-crust", "gfx/invobjs/meat-crab");
        assertLayers(entryNamed(entries, "Raw Lobster"),
                "gfx/invobjs/meat-crust", "gfx/invobjs/meat-lobster");
    }

    private static JSONObject entryNamed(List<JSONObject> entries, String name) {
        return entries.stream()
                .filter(entry -> name.equals(entry.optString("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing entry: " + name));
    }

    private static void assertLayers(JSONObject entry, String first, String second) {
        JSONArray layers = entry.getJSONArray("layer");
        assertEquals(2, layers.length());
        assertEquals(first, layers.getString(0));
        assertEquals(second, layers.getString(1));
    }
}
