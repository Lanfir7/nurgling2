package nurgling.tools;

import nurgling.widgets.Specialisation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecDuckInfrastructureTest {
    @Test
    void duckAreaSpecialisationsCanBeParsed() {
        assertEquals("duck", Specialisation.SpecName.valueOf("duck").name());
        assertEquals("duckIncubator", Specialisation.SpecName.valueOf("duckIncubator").name());
    }

    @Test
    void duckProductsAreRecognizedByStorageCategories() {
        assertTrue(hasStatic("Egg", "gfx/invobjs/egg-duck"));
        assertTrue(hasStatic("Dead Animal Carcass", "gfx/invobjs/duckdrake-dead"));
        assertTrue(hasStatic("Dead Animal Carcass", "gfx/invobjs/duckhen-dead"));
        assertTrue(hasStatic("Clean Animal Carcass", "gfx/invobjs/duck-cleaned"));
        assertTrue(hasStatic("Clean Bird Carcass", "gfx/invobjs/duck-cleaned"));
        assertTrue(hasLayer("Poultry", "gfx/invobjs/meat-duck"));
        assertTrue(hasStatic("Feather", "gfx/invobjs/feather-duck"));
    }

    private static boolean hasStatic(String category, String resource) {
        ArrayList<JSONObject> entries = VSpec.categories.get(category);
        return entries != null && entries.stream()
                .anyMatch(entry -> resource.equals(entry.optString("static")));
    }

    private static boolean hasLayer(String category, String resource) {
        ArrayList<JSONObject> entries = VSpec.categories.get(category);
        if(entries == null)
            return false;
        for(JSONObject entry : entries) {
            JSONArray layers = entry.optJSONArray("layer");
            if(layers == null)
                continue;
            for(int i = 0; i < layers.length(); i++)
                if(resource.equals(layers.optString(i)))
                    return true;
        }
        return false;
    }
}
