package nurgling.widgets.craftatlas;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasWikiIconsTest {
    @Test
    void everyWikiProductIngredientAndStationHasABundledIcon() throws Exception {
        JSONObject recipes = readJson("/nurgling/craftatlas/wiki-reference.json");
        JSONObject manifest = readJson("/nurgling/craftatlas/wiki-icons.json");
        Set<String> expected = new HashSet<>();
        JSONArray entries = recipes.getJSONArray("entries");
        for(int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            expected.add(entry.getString("name"));
            collectNames(entry.optJSONArray("inputs"), expected);
            collectNamedRequirements(entry.optJSONArray("requirements"), expected);
        }
        JSONObject icons = manifest.getJSONObject("icons");
        assertEquals(expected.size(), icons.length());
        for(String name : expected)
            assertTrue(icons.has(name), "missing icon for " + name);

        JSONArray sheets = manifest.getJSONArray("sheets");
        assertTrue(sheets.length() > 0);
        for(int i = 0; i < sheets.length(); i++) {
            String path = "/nurgling/craftatlas/" + sheets.getString(i);
            try(InputStream input = CraftAtlasWikiIconsTest.class.getResourceAsStream(path)) {
                assertNotNull(input, path);
                assertNotNull(ImageIO.read(input), path);
            }
        }
    }

    private static void collectNames(JSONArray values, Set<String> names) {
        if(values == null) return;
        for(int i = 0; i < values.length(); i++) names.add(values.getJSONObject(i).getString("name"));
    }

    private static void collectNamedRequirements(JSONArray values, Set<String> names) {
        if(values == null) return;
        for(int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            if(!value.optString("resource", "").isEmpty()) names.add(value.getString("name"));
        }
    }

    private static JSONObject readJson(String path) throws Exception {
        try(InputStream input = CraftAtlasWikiIconsTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            byte[] data = input.readAllBytes();
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        }
    }
}
