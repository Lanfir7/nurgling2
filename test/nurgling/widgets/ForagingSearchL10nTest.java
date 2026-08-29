package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForagingSearchL10nTest {

    @Test
    void englishDeleteAllLabel() throws Exception {
        Properties p = load("src/lang/messages.properties");
        assertEquals("Delete all", p.getProperty("foraging.search.delete_all"));
    }

    @Test
    void russianDeleteAllLabel() throws Exception {
        Properties p = load("src/lang/messages_ru.properties");
        assertEquals("Удалить всё", p.getProperty("foraging.search.delete_all"));
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
