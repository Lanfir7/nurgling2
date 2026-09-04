package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasLocalizationTest {
    private static final List<String> SLOT_CODES = Arrays.asList(
            "1l", "1r", "2l", "2r", "3l", "3r", "4l", "4r", "5l", "5r", "6l", "6r",
            "7l", "7r", "8l", "8r", "9l", "9r", "10l", "10r", "11l", "11r");

    @Test
    void englishContainsEveryCraftAtlasEquipmentKey() throws Exception {
        assertComplete(load("src/lang/messages.properties"));
    }

    @Test
    void russianContainsEveryCraftAtlasEquipmentKey() throws Exception {
        assertComplete(load("src/lang/messages_ru.properties"));
    }

    private static void assertComplete(Properties properties) {
        List<String> required = new ArrayList<>();
        for(String section : CraftAtlasSections.MAIN)
            required.add("craft_atlas.section." + section);
        required.add("craft_atlas.section.back");
        for(String section : CraftAtlasSections.EQUIPMENT)
            required.add("craft_atlas.section." + section);
        required.addAll(Arrays.asList(
                "craft_atlas.quality_hint", "craft_atlas.quality", "craft_atlas.gilding",
                "craft_atlas.bonuses", "craft_atlas.equipment_slots", "craft_atlas.quality_modifiers",
                "craft_atlas.open_recipe", "craft_atlas.equipment_slot.optional", "craft_atlas.auto",
                "craft_atlas.inventory", "craft_atlas.material.ignore", "craft_atlas.material.all",
                "craft_atlas.material.missing", "craft_atlas.craft_count_hint",
                "craft_atlas.collect_resources", "craft_atlas.collect_bad_count",
                "craft_atlas.collect_unavailable", "craft_atlas.collect_missing",
                "craft_atlas.collect_shortage", "craft_atlas.quality_unavailable"));
        for(String code : SLOT_CODES)
            required.add("craft_atlas.equipment_slot." + code);

        List<String> missing = new ArrayList<>();
        for(String key : required)
            if(properties.getProperty(key) == null || properties.getProperty(key).trim().isEmpty()) missing.add(key);
        assertTrue(missing.isEmpty(), "Missing Craft Atlas translations: " + missing);
    }

    private static Properties load(String path) throws Exception {
        Properties properties = new Properties();
        try(InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
