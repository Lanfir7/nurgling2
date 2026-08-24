package nurgling.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VSpecSharpToolTest {
    private static final List<String> WIKI_SHARP_TOOLS = List.of(
            "Bronze Sword",
            "Butcher's cleaver",
            "Ceramic Knife",
            "Flint Knife",
            "Fyrdsman's Sword",
            "Hirdsman's Sword",
            "Metal Axe",
            "Obsidian Dagger",
            "Stone Axe",
            "Tinker's Throwing Axe",
            "Woodsman's Axe"
    );

    @Test
    void sharpToolMatchesCurrentWikiListOnly() {
        ArrayList<JSONObject> entries = VSpec.categories.get("Sharp Tool");
        List<String> names = entries.stream().map(o -> o.getString("name")).collect(Collectors.toList());
        assertEquals(WIKI_SHARP_TOOLS, names);

        NAlias sharp = VSpec.getNamesInCategory("Sharp Tool");
        for (String name : WIKI_SHARP_TOOLS) {
            assertTrue(sharp.matches(name), name);
        }
        assertFalse(sharp.matches("Pickaxe"));
        assertFalse(sharp.matches("Scythe"));
        assertFalse(sharp.matches("Bonesaw"));
        assertFalse(sharp.matches("Shears"));
        assertFalse(sharp.matches("Battle Axe of the Twelfth Bay"));
    }
}
