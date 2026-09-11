package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level checks so this file compiles without the client. */
class FuelOvensActionTest {

    @Test
    void fuelsFourInventoryBranchesPerOvenBeforeLighting() throws Exception {
        String bot = read("src/nurgling/actions/FuelOvens.java");
        assertTrue(bot.contains("BRANCHES_PER_OVEN = 4"), bot);
        assertTrue(bot.contains("branch < BRANCHES_PER_OVEN"), bot);
        assertTrue(bot.contains("gui.getInventory().getItems(BRANCH)"), bot);
        assertTrue(bot.contains("new LightGob(toLight, 4)"), bot);
        assertTrue(bot.indexOf("fuelOven(gui, oven)") < bot.indexOf("new LightGob(toLight, 4)"), bot);
        assertFalse(bot.contains("FuelToContainers"), bot);
        assertFalse(bot.contains("KilnFuelCatalog"), bot);
        assertFalse(bot.contains("findSpec"), bot);
        assertFalse(bot.contains("BotRegistry"), bot);
    }

    @Test
    void actionAppliesOnlyToOvensAndIsRegistered() throws Exception {
        String action = read("src/nurgling/contextmenu/FuelOvensAction.java");
        assertTrue(action.contains("OvenGobs.matches"), action);
        assertFalse(action.contains("isUiAction"), action);
        assertTrue(action.contains("return new FuelOvens"), action);
        String registry = read("src/nurgling/contextmenu/GobContextRegistry.java");
        assertTrue(registry.contains("register(new FuelOvensAction());"), registry);
    }

    @Test
    void labelsAreFuelAndLightOvens() throws Exception {
        Properties en = load("src/lang/messages.properties");
        Properties ru = load("src/lang/messages_ru.properties");
        assertTrue("Fuel and light ovens".equals(en.getProperty("context.fuel_ovens")),
                String.valueOf(en.getProperty("context.fuel_ovens")));
        assertTrue("Заправить и поджечь печи".equals(ru.getProperty("context.fuel_ovens")),
                String.valueOf(ru.getProperty("context.fuel_ovens")));
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
