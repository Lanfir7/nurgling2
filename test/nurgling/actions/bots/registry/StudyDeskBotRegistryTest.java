package nurgling.actions.bots.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StudyDeskBotRegistryTest {
    @Test
    void existingStudytableBotKeepsOwnedDeskBehavior() {
        BotDescriptor bot = BotRegistry.byId("studytable");

        assertNotNull(bot);
        assertEquals(Boolean.FALSE, bot.defaultSettings.get("fillAll"));
    }

    @Test
    void separateBotExplicitlyFillsAllDesks() {
        BotDescriptor bot = BotRegistry.byId("studytable_all");

        assertNotNull(bot);
        assertEquals(Boolean.TRUE, bot.defaultSettings.get("fillAll"));
    }

    @Test
    void allDesksBotUsesDedicatedIconResource() {
        BotDescriptor bot = BotRegistry.byId("studytable_all");

        assertNotNull(bot);
        assertEquals("nurgling/bots/icons/studytable_all/u", bot.getUpIconPath());
        assertEquals("nurgling/bots/icons/studytable_all/d", bot.getDownIconPath());
        assertEquals("nurgling/bots/icons/studytable_all/h", bot.getHoverIconPath());
    }
}
