package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackEquipL10nTest {

    @Test
    void englishEquipOnAttackLabel() throws Exception {
        Properties p = load("src/lang/messages.properties");
        assertEquals("Equip sword and shield on Attack", p.getProperty("qol.equip_sword_shield_on_attack"));
    }

    @Test
    void russianEquipOnAttackLabel() throws Exception {
        Properties p = load("src/lang/messages_ru.properties");
        assertEquals("Экипировать меч и щит при Атаке", p.getProperty("qol.equip_sword_shield_on_attack"));
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
