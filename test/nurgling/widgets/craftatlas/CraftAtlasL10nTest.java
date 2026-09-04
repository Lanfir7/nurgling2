package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasL10nTest {
    private static final String[] KEYS = {
            "craft_atlas.title", "craft_atlas.search_placeholder", "craft_atlas.open_craft",
            "craft_atlas.inputs", "craft_atlas.requirements", "craft_atlas.back", "craft_atlas.forward",
            "craft_atlas.status.open", "craft_atlas.status.unavailable", "craft_atlas.status.reference",
            "craft_atlas.requirement.station", "craft_atlas.requirement.tool", "craft_atlas.requirement.skill",
            "craft_atlas.requirement.discovery", "craft_atlas.choice", "craft_atlas.cycle", "craft_atlas.no_recipe",
            "craft_atlas.normal_craft_hint", "craft_atlas.section.all", "craft_atlas.section.favorites",
            "craft_atlas.section.recent", "craft_atlas.section.gildings", "craft_atlas.section.foods"
    };

    @Test
    void everyAtlasKeyExistsInEnglishAndRussian() throws Exception {
        for(String file : Arrays.asList("src/lang/messages.properties", "src/lang/messages_ru.properties")) {
            Properties p = new Properties();
            try(InputStream in = Files.newInputStream(Paths.get(file))) { p.load(new java.io.InputStreamReader(in, "UTF-8")); }
            for(String key : KEYS) assertTrue(p.containsKey(key), file + " missing " + key);
            assertFalse(p.containsKey("craft_atlas.compare"));
            assertFalse(p.containsKey("craft_atlas.check_materials"));
            assertFalse(p.containsKey("craft_atlas.details"));
        }
    }
}
