package nurgling.actions.bots.registry;

import nurgling.actions.bots.WormFarmer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WormFarmBotRegistryTest {
    @Test
    void wormFarmSitsNextToLeveler() {
        BotDescriptor bot = BotRegistry.byId("wormfarm");
        assertNotNull(bot);
        assertEquals(BotDescriptor.BotType.UTILS, bot.type);
        assertEquals(WormFarmer.class, bot.clazz);
        assertEquals("wormfarm", bot.iconPath);
        assertEquals("bot.wormfarm.title", bot.titleKey);
        assertEquals("bot.wormfarm.desc", bot.descriptionKey);
        assertTrue(bot.allowedAsStepInScenario);
        assertTrue(bot.allowedAsItemInBotMenu);
        List<BotDescriptor> all = BotRegistry.all();
        int leveler = -1;
        for (int i = 0; i < all.size(); i++) {
            if ("leveler".equals(all.get(i).id)) leveler = i;
        }
        assertTrue(leveler >= 0);
        assertEquals("wormfarm", all.get(leveler + 1).id);
    }

    @Test
    void wormFarmIconsExist() throws Exception {
        Path icons = Path.of("resources/src/nurgling/bots/icons/wormfarm");
        for (String st : new String[] {"u", "h", "d"}) {
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/image/image_0.png")), st + " png");
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/image/image_0.data")), st + " data");
            assertTrue(Files.isRegularFile(icons.resolve(st + ".res/meta")), st + " meta");
        }
        String title = Files.readString(icons.resolve("u.res/tooltip/tooltip_0.data"));
        String desc = Files.readString(icons.resolve("u.res/tooltip/tooltip_1.data"));
        assertTrue(title.contains("@bot.wormfarm.title"), title);
        assertTrue(desc.contains("@bot.wormfarm.desc"), desc);
        assertTrue(!title.contains("leveler") && !desc.contains("leveler"));
    }
}
