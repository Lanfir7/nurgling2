package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KilnFuelL10nTest {

    @Test
    void englishMenuAndWindowLabels() throws Exception {
        Properties p = load("src/lang/messages.properties");
        assertEquals("Kiln fuel", p.getProperty("context.kiln_fuel"));
        assertEquals("Kiln fuel", p.getProperty("kiln_fuel.title"));
        assertEquals("Item", p.getProperty("kiln_fuel.col.item"));
        assertEquals("Branches", p.getProperty("kiln_fuel.col.fuel"));
        assertEquals("Real time", p.getProperty("kiln_fuel.col.real"));
        assertEquals("In-game", p.getProperty("kiln_fuel.col.ingame"));
        assertEquals("Notes", p.getProperty("kiln_fuel.col.notes"));
    }

    @Test
    void russianMenuAndWindowLabels() throws Exception {
        Properties p = load("src/lang/messages_ru.properties");
        assertEquals("Топливо печи", p.getProperty("context.kiln_fuel"));
        assertEquals("Топливо печи", p.getProperty("kiln_fuel.title"));
        assertEquals("Предмет", p.getProperty("kiln_fuel.col.item"));
        assertEquals("Ветки", p.getProperty("kiln_fuel.col.fuel"));
        assertEquals("Реальное", p.getProperty("kiln_fuel.col.real"));
        assertEquals("Игровое", p.getProperty("kiln_fuel.col.ingame"));
        assertEquals("Заметки", p.getProperty("kiln_fuel.col.notes"));
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
