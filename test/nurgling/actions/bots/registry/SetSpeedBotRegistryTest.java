package nurgling.actions.bots.registry;

import nurgling.actions.bots.SetSpeedBot;
import nurgling.i18n.L10n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetSpeedBotRegistryTest {
    private String previousLanguage;

    @BeforeEach
    void saveLanguage() {
        previousLanguage = L10n.getLanguage();
    }

    @AfterEach
    void restoreLanguage() {
        L10n.setLanguage(previousLanguage);
    }

    @Test
    void setSpeedIsAScenarioOnlyStepWithRunAsDefault() {
        BotDescriptor bot = BotRegistry.byId("set_speed");

        assertNotNull(bot);
        assertEquals(BotDescriptor.BotType.UTILS, bot.type);
        assertTrue(bot.allowedAsStepInScenario);
        assertFalse(bot.allowedAsItemInBotMenu);
        assertEquals(SetSpeedBot.class, bot.clazz);
        assertEquals("speed", bot.iconPath);
        assertEquals(2, bot.defaultSettings.get("speed"));
    }

    @Test
    void setSpeedHasButtonAssetsForEveryState() {
        Path icons = Path.of("resources/src/nurgling/bots/icons/speed");
        for (String state : new String[] {"u", "h", "d"}) {
            assertTrue(Files.isRegularFile(icons.resolve(state + ".res/image/image_0.png")), state + " png");
            assertTrue(Files.isRegularFile(icons.resolve(state + ".res/image/image_0.data")), state + " data");
            assertTrue(Files.isRegularFile(icons.resolve(state + ".res/meta")), state + " metadata");
        }
    }

    @Test
    void setSpeedTextIsAvailableInEnglishAndRussian() {
        L10n.setLanguage("en");
        assertEquals("Set Movement Speed", L10n.get("bot.set_speed.title"));
        assertEquals("Speed:", L10n.get("scenario.speed.label"));

        L10n.setLanguage("ru");
        assertEquals("Скорость движения", L10n.get("bot.set_speed.title"));
        assertEquals("Скорость:", L10n.get("scenario.speed.label"));
    }
}
