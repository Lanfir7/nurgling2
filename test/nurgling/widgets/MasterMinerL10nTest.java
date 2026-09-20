package nurgling.widgets;

import nurgling.actions.bots.registry.BotDescriptor;
import nurgling.actions.bots.registry.BotRegistry;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerL10nTest {

    private static final String[] KEYS = {
            "maptools.kind.ore",
            "maptools.kind.gem",
            "maptools.kind.stone",
            "maptools.gem_icons_tip",
            "maptools.stone_icons_tip",
            "mineral.search_title",
            "mineral.category",
            "mineral.type",
            "mineral.min_quality",
            "mineral.search_any",
            "mineral.search_tip",
            "common.search",
            "common.results",
            "bot.masterminer.title",
            "bot.masterminer.desc",
            "bot.masterminer.masonry",
            "bot.masterminer.last_mined",
            "bot.masterminer.q_legend",
            "bot.masterminer.ground_pickup",
            "bot.masterminer.drop_threshold",
            "bot.masterminer.keep_stones",
            "bot.masterminer.switch",
            "bot.masterminer.reset_all",
            "bot.masterminer.set",
            "nsettings.item.mining_mastery",
            "nsettings.mining_mastery.select",
            "nsettings.mining_mastery.threshold_stones",
            "nsettings.mining_mastery.threshold_ores",
            "nsettings.mining_mastery.click_right"
    };

    @Test
    void englishKeysPresentNonEmpty() throws Exception {
        assertKeysPresent(load("src/lang/messages.properties"));
    }

    @Test
    void russianKeysPresentNonEmpty() throws Exception {
        assertKeysPresent(load("src/lang/messages_ru.properties"));
    }

    @Test
    void masterMinerRegistryUsesBotTitleKey() {
        BotDescriptor bot = BotRegistry.byId("masterminer");
        assertNotNull(bot);
        assertTrue(bot.titleKey.startsWith("bot."));
    }

    @Test
    void settingsWindowUsesMiningMasteryKey() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/NSettingsWindow.java"), StandardCharsets.UTF_8);
        assertTrue(src.contains("nsettings.item.mining_mastery"));
        assertFalse(src.contains("\"Mining Mastery\""));
    }

    @Test
    void masterMinerWindowUsesTitleKey() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/bots/MasterMinerWnd.java"), StandardCharsets.UTF_8);
        assertTrue(src.contains("bot.masterminer.title"));
        assertFalse(src.contains("\"Master Miner\""));
    }

    private static void assertKeysPresent(Properties p) {
        for (String key : KEYS) {
            String value = p.getProperty(key);
            assertNotNull(value, key);
            assertFalse(value.isBlank(), key);
        }
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
