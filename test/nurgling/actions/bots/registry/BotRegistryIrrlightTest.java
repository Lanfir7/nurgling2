package nurgling.actions.bots.registry;

import nurgling.actions.bots.IrrlightBot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotRegistryIrrlightTest {
    @Test
    void irrlightBotIsRegisteredWithOwnIconAndL10nKeys() {
        BotDescriptor bot = BotRegistry.byId("irrlight");
        assertNotNull(bot);
        assertEquals(BotDescriptor.BotType.PRODUCTIONS, bot.type);
        assertEquals(IrrlightBot.class, bot.clazz);
        assertEquals("irrlight", bot.iconPath);
        assertEquals("bot.irrlight.title", bot.titleKey);
        assertEquals("bot.irrlight.desc", bot.descriptionKey);
        assertFalse(bot.allowedAsStepInScenario);
        assertTrue(bot.allowedAsItemInBotMenu);
        assertFalse(bot.disStacks);
    }
}
