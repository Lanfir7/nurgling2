package nurgling.actions.bots.registry;

import nurgling.actions.bots.VeinMiner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotRegistryVeinMinerTest {
    @Test
    void veinMinerSitsNextToMasterMiner() {
        BotDescriptor bot = BotRegistry.byId("veinminer");
        assertNotNull(bot);
        assertEquals(BotDescriptor.BotType.RESOURCES, bot.type);
        assertEquals(VeinMiner.class, bot.clazz);
        assertEquals("veinminer", bot.iconPath);
        assertEquals("bot.veinminer.title", bot.titleKey);
        assertEquals("bot.veinminer.desc", bot.descriptionKey);
        assertFalse(bot.allowedAsStepInScenario);
        assertTrue(bot.allowedAsItemInBotMenu);
        List<BotDescriptor> all = BotRegistry.all();
        int master = -1;
        for (int i = 0; i < all.size(); i++) {
            if ("masterminer".equals(all.get(i).id)) master = i;
        }
        assertTrue(master >= 0);
        assertEquals("veinminer", all.get(master + 1).id);
    }

    @Test
    void veinMinerIconsUseOwnTooltipKeys() throws Exception {
        Path icons = Path.of("resources/src/nurgling/bots/icons/veinminer");
        for (String st : new String[] {"u", "h", "d"}) {
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/image/image_0.png")), st + " png");
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/image/image_0.data")), st + " data");
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/meta")), st + " meta");
        }
        String title = Files.readString(icons.resolve("u.res/tooltip/tooltip_0.data"));
        String desc = Files.readString(icons.resolve("u.res/tooltip/tooltip_1.data"));
        assertTrue(title.contains("@bot.veinminer.title"), title);
        assertTrue(desc.contains("@bot.veinminer.desc"), desc);
        assertFalse(title.contains("masterminer"));
        assertFalse(desc.contains("masterminer"));
        assertFalse(title.contains("МастерМайнер"));
    }
}
