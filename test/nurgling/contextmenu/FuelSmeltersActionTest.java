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
class FuelSmeltersActionTest {

    @Test
    void botFuelsThenLightsAndIsNotInBotRegistry() throws Exception {
        String bot = read("src/nurgling/actions/FuelSmelters.java");
        int fuel = bot.indexOf("FuelToContainers");
        int light = bot.indexOf("LightGob");
        assertTrue(fuel >= 0 && light >= 0, bot);
        assertTrue(fuel < light, "must light after fuel: " + bot);
        assertTrue(bot.contains("primsmelter"), "must exclude stack furnace via primsmelter");
        assertTrue(bot.contains("setMaxlvl(12)"), bot);
        assertTrue(bot.contains("setCredolvl(9)"), bot);
        assertTrue(bot.contains("setFueltype(\"coal\")"), bot);
        assertTrue(bot.contains("Ore Smelter") && bot.contains("Smith's Smelter"), bot);
        assertFalse(bot.contains("BotRegistry"));
        assertFalse(bot.contains("gfx/terobjs/primsmelter") && bot.contains("Stack furnace"),
                "must not include Stack Furnace");
        String registry = read("src/nurgling/actions/bots/registry/BotRegistry.java");
        assertFalse(registry.contains("FuelSmelters"));
    }

    @Test
    void actionIsBotNotUiOnlyAndAppliesOnlyToOreAndSmithSmelter() throws Exception {
        String src = read("src/nurgling/contextmenu/FuelSmeltersAction.java");
        assertTrue(src.contains("SmelterGobs.matches"), src);
        assertFalse(src.contains("isUiAction"), "must keep default isUiAction=false (M badge)");
        assertTrue(src.contains("return new FuelSmelters") || src.contains("return new nurgling.actions.FuelSmelters"), src);
        String gobs = read("src/nurgling/contextmenu/SmelterGobs.java");
        assertTrue(gobs.contains("primsmelter"), gobs);
        assertTrue(gobs.contains("gfx/terobjs/smelter"), gobs);
        int prim = gobs.indexOf("primsmelter");
        int smelter = gobs.indexOf("gfx/terobjs/smelter");
        assertTrue(prim >= 0 && smelter >= 0 && prim < smelter, "check primsmelter first: " + gobs);
    }

    @Test
    void registeredAfterFuelKilns() throws Exception {
        String src = read("src/nurgling/contextmenu/GobContextRegistry.java");
        int light = src.indexOf("register(new LightAction());");
        int kilns = src.indexOf("register(new FuelKilnsAction());");
        int smelters = src.indexOf("register(new FuelSmeltersAction());");
        assertTrue(light >= 0 && kilns >= 0 && smelters >= 0, src);
        assertTrue(light < kilns && kilns < smelters, src);
        assertFalse(src.contains("BotRegistry"));
    }

    @Test
    void labelsAreFuelSmelters() throws Exception {
        Properties en = load("src/lang/messages.properties");
        Properties ru = load("src/lang/messages_ru.properties");
        assertTrue("Fuel smelters".equals(en.getProperty("context.fuel_smelters")),
                String.valueOf(en.getProperty("context.fuel_smelters")));
        assertTrue("Заправить и поджечь печи".equals(ru.getProperty("context.fuel_smelters")),
                String.valueOf(ru.getProperty("context.fuel_smelters")));
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
