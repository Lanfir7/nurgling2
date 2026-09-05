package nurgling.widgets;

import nurgling.tools.DayCycleEvents;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NCalMantleL10nTest {

    private static final double WORLD_SPEED = 3.29;

    @Test
    void englishInAndOutOfWindow() throws Exception {
        Properties p = load("src/lang/messages.properties");
        DayCycleEvents.MantleEta inside = DayCycleEvents.mantleEta(6, 0, WORLD_SPEED);
        DayCycleEvents.MantleEta outside = DayCycleEvents.mantleEta(4, 0, WORLD_SPEED);
        String name = p.getProperty("calendar.mantle_name");
        assertEquals("Dewy Lady's Mantle (00:22 RL left)",
                     String.format(p.getProperty("calendar.mantle_left"), name, inside.rlHours, inside.rlMinutes));
        assertEquals("Dewy Lady's Mantle in 00:13 RL",
                     String.format(p.getProperty("calendar.mantle_in"), name, outside.rlHours, outside.rlMinutes));
    }

    @Test
    void russianInAndOutOfWindow() throws Exception {
        Properties p = load("src/lang/messages_ru.properties");
        DayCycleEvents.MantleEta inside = DayCycleEvents.mantleEta(6, 0, WORLD_SPEED);
        DayCycleEvents.MantleEta outside = DayCycleEvents.mantleEta(4, 0, WORLD_SPEED);
        String name = p.getProperty("calendar.mantle_name");
        assertEquals("Росистая манжетка (00:22 РВ ост.)",
                     String.format(p.getProperty("calendar.mantle_left"), name, inside.rlHours, inside.rlMinutes));
        assertEquals("Росистая манжетка через 00:13 РВ",
                     String.format(p.getProperty("calendar.mantle_in"), name, outside.rlHours, outside.rlMinutes));
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
