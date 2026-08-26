package nurgling.actions.bots.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BotRegistryProspectMineTest {
    @Test
    void prospectMineUsesOwnIconAndL10nKeys() {
        BotDescriptor bot = BotRegistry.byId("prospect_mine");
        assertNotNull(bot);
        assertEquals("prospect_mine", bot.iconPath);
        assertEquals("bot.prospect_mine.title", bot.titleKey);
        assertEquals("bot.prospect_mine.desc", bot.descriptionKey);
    }
}
