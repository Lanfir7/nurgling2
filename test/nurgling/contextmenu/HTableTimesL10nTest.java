package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HTableTimesL10nTest {

    @Test
    void englishMenuAndWindowLabels() throws Exception {
        Properties p = load("src/lang/messages.properties");
        assertEquals("Table times", p.getProperty("context.htable_times"));
        assertEquals("Herbalist table", p.getProperty("htable_times.title"));
        assertEquals("Item", p.getProperty("htable_times.col.item"));
        assertEquals("Product", p.getProperty("htable_times.col.product"));
        assertEquals("Real time", p.getProperty("htable_times.col.real"));
        assertEquals("In-game", p.getProperty("htable_times.col.ingame"));
        assertNull(p.getProperty("htable_times.col.notes"));
    }

    @Test
    void russianMenuAndWindowLabels() throws Exception {
        Properties p = load("src/lang/messages_ru.properties");
        assertEquals("Время стола", p.getProperty("context.htable_times"));
        assertEquals("Стол травника", p.getProperty("htable_times.title"));
        assertEquals("Предмет", p.getProperty("htable_times.col.item"));
        assertEquals("Продукт", p.getProperty("htable_times.col.product"));
        assertEquals("Реальное", p.getProperty("htable_times.col.real"));
        assertEquals("Игровое", p.getProperty("htable_times.col.ingame"));
        assertNull(p.getProperty("htable_times.col.notes"));
    }

    private static Properties load(String path) throws Exception {
        Path file = Paths.get(path);
        Properties p = new Properties();
        try (InputStreamReader in = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF-8)) {
            p.load(in);
        }
        return p;
    }
}
