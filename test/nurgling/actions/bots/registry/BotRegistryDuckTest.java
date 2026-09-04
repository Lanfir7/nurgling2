package nurgling.actions.bots.registry;

import nurgling.actions.bots.DuckMaster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotRegistryDuckTest {
    @Test
    void duckManagerIsAvailableInTheFarmingMenu() {
        BotDescriptor bot = BotRegistry.byId("duck");

        assertNotNull(bot);
        assertEquals(BotDescriptor.BotType.FARMING, bot.type);
        assertEquals(DuckMaster.class, bot.clazz);
        assertEquals("duck", bot.iconPath);
        assertEquals("bot.duck.title", bot.titleKey);
        assertEquals("bot.duck.desc", bot.descriptionKey);
        assertTrue(bot.allowedAsStepInScenario);
        assertTrue(bot.allowedAsItemInBotMenu);
    }
}
