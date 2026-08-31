package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level checks so this file compiles without constructing a Gob.
 */
class SpitRoastContextActionTest {

    @Test
    void actionStartsFriedFishAndAppliesOnlyToBonfire() throws Exception {
        String src = read("src/nurgling/contextmenu/SpitRoastContextAction.java");
        assertTrue(src.contains("PowGobs.matches"), src);
        assertFalse(src.contains("isUiAction"), "must keep default isUiAction=false (M badge)");
        assertTrue(src.contains("return new FriedFish") || src.contains("return new nurgling.actions.bots.FriedFish"), src);
    }

    @Test
    void registeredWithOtherGobMacros() throws Exception {
        String src = read("src/nurgling/contextmenu/GobContextRegistry.java");
        int roast = src.indexOf("register(new SpitRoastContextAction());");
        int dryFish = src.indexOf("register(new DryFishContextAction());");
        int configure = src.indexOf("register(new ConfigureGobAction());");
        assertTrue(roast >= 0 && dryFish >= 0 && configure >= 0, src);
        assertTrue(roast < configure, src);
    }

    @Test
    void labelsAreSpitRoast() throws Exception {
        Properties en = load("src/lang/messages.properties");
        Properties ru = load("src/lang/messages_ru.properties");
        assertTrue("Spit Roast".equals(en.getProperty("context.spit_roast")),
                String.valueOf(en.getProperty("context.spit_roast")));
        assertTrue("Жарка на вертеле".equals(ru.getProperty("context.spit_roast")),
                String.valueOf(ru.getProperty("context.spit_roast")));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static Properties load(String path) throws Exception {
        Properties p = new Properties();
        try (java.io.InputStreamReader in = new java.io.InputStreamReader(
                Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }
}
