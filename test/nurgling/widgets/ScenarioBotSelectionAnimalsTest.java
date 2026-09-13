package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioBotSelectionAnimalsTest {

    @Test
    void dialogSourceWiresAnimalsCategoryNotLivestock() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/ScenarioBotSelectionDialog.java"),
                StandardCharsets.UTF_8);
        String compact = src.replaceAll("\\s+", "");

        assertTrue(src.contains("List.of(RESOURCES, UTILS, PRODUCTIONS, FARMING, FARMING_QUALITY, ANIMALS)"));
        assertTrue(compact.contains("caseANIMALS:title=L10n.get(\"botselect.animals\");break;"));
        assertTrue(src.contains("Predicate<BotDescriptor> filter"));
        assertTrue(compact.contains(".filter(filter)"));
        assertFalse(src.contains("ScenarioBotSelectionGroups"));
        assertFalse(src.contains("static List<BotDescriptor.BotType> groupOrder()"));
        assertFalse(src.contains("static String titleKey("));
        assertTrue(src.contains("ANIMALS"));
        assertTrue(src.contains("botselect.animals"));
        assertFalse(src.contains("LIVESTOCK"));
        assertFalse(src.contains("botselect.livestock"));
        assertFalse(src.contains("bot.type.livestock"));
    }

    @Test
    void englishHasAnimalsKeysAndNoLivestock() throws Exception {
        Properties p = load("src/lang/messages.properties");
        assertEquals("Animals", p.getProperty("bot.type.animals"));
        assertEquals("Animals", p.getProperty("botselect.animals"));
        assertFalse(p.containsKey("bot.type.livestock"));
        assertFalse(p.containsKey("botselect.livestock"));
    }

    @Test
    void russianHasAnimalsKeysAndNoLivestock() throws Exception {
        Properties p = load("src/lang/messages_ru.properties");
        assertEquals("Животные", p.getProperty("bot.type.animals"));
        assertEquals("Животные", p.getProperty("botselect.animals"));
        assertFalse(p.containsKey("bot.type.livestock"));
        assertFalse(p.containsKey("botselect.livestock"));
    }

    private static Properties load(String path) throws Exception {
        Path file = Paths.get(path);
        Properties p = new Properties();
        try (InputStreamReader in = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }
}
