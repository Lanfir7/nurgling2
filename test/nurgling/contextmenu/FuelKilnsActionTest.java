package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level checks so this file compiles without the client
 * (Gob, NMapView, TerrainSearchWindow, ...).
 */
class FuelKilnsActionTest {

    @Test
    void botDoesNotLightAndIsNotInBotRegistry() throws Exception {
        String bot = read("src/nurgling/actions/FuelKilns.java");
        assertFalse(bot.contains("LightGob"));
        assertFalse(bot.contains("BotRegistry"));
        String registry = read("src/nurgling/actions/bots/registry/BotRegistry.java");
        assertFalse(registry.contains("FuelKilns"));
    }

    @Test
    void actionIsBotNotUiOnlyAndAppliesOnlyToKiln() throws Exception {
        String src = read("src/nurgling/contextmenu/FuelKilnsAction.java");
        assertTrue(src.contains("KilnGobs.matches"), src);
        assertFalse(src.contains("isUiAction"), "must keep default isUiAction=false (M badge)");
        assertTrue(src.contains("return new FuelKilns") || src.contains("return new nurgling.actions.FuelKilns"), src);
    }

    @Test
    void registeredAfterLightAndBeforeKilnFuelTable() throws Exception {
        String src = read("src/nurgling/contextmenu/GobContextRegistry.java");
        int light = src.indexOf("register(new LightAction());");
        int fuel = src.indexOf("register(new FuelKilnsAction());");
        int table = src.indexOf("register(new KilnFuelAction());");
        assertTrue(light >= 0 && fuel >= 0 && table >= 0, src);
        assertTrue(light < fuel && fuel < table, src);
        assertFalse(src.contains("BotRegistry"));
    }

    @Test
    void labelsAreFuelKilns() throws Exception {
        Properties en = load("src/lang/messages.properties");
        Properties ru = load("src/lang/messages_ru.properties");
        assertTrue("Fuel kilns".equals(en.getProperty("context.fuel_kilns")), String.valueOf(en.getProperty("context.fuel_kilns")));
        assertTrue("Заправить печи".equals(ru.getProperty("context.fuel_kilns")), String.valueOf(ru.getProperty("context.fuel_kilns")));
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
