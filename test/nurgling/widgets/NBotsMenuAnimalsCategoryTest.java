package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NBotsMenuAnimalsCategoryTest {
    @Test
    void constructorDefinesMenuOrderAndLooksUpByBotType() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/NBotsMenu.java"), StandardCharsets.UTF_8);
        String compact = src.replaceAll("\\s+", "");

        assertFalse(src.contains("LIVESTOCK"),
                "NBotsMenu must not still use LIVESTOCK");
        assertFalse(src.contains("static List<BotDescriptor.BotType> menuOrder()"),
                "menuOrder must be a local constructor variable, not a helper");
        assertFalse(src.contains("static Map<BotDescriptor.BotType, String> layoutNames()"),
                "layoutNames must be a local constructor variable, not a helper");
        assertFalse(src.contains("static BotDescriptor.BotType menuGroup"),
                "must not extract menuGroup for tests");
        assertTrue(compact.contains("layouts.get(bot.type)"),
                "lookup must use bot.type directly");
        assertFalse(compact.contains("layouts.get(menuGroup(bot))"),
                "must not route grouping through menuGroup");
        assertTrue(compact.contains("BotDescriptor.BotType.FARMING,BotDescriptor.BotType.FARMING_QUALITY,BotDescriptor.BotType.ANIMALS"),
                "ANIMALS must follow FARMING_QUALITY in menuOrder");
        assertTrue(compact.contains("BotDescriptor.BotType.ANIMALS,\"animals\""),
                "ANIMALS layout name must be animals");
        assertFalse(compact.contains("BotType.ANIMALS)?BotDescriptor.BotType.FARMING"),
                "must not map ANIMALS to FARMING");
        assertFalse(compact.contains("BotType.LIVESTOCK)?BotDescriptor.BotType.FARMING"),
                "must not map LIVESTOCK to FARMING");
        assertFalse(compact.contains("==BotDescriptor.BotType.ANIMALS)?BotDescriptor.BotType.FARMING"),
                "must not collapse ANIMALS into FARMING");
        assertTrue(compact.contains("BotDescriptor.BotType.BATTLE"));
        assertTrue(compact.contains("BotDescriptor.BotType.BUILD"));
        assertTrue(compact.contains("BotDescriptor.BotType.TOOLS"));
        assertTrue(compact.contains("BotDescriptor.BotType.FARMING,\"farming\""));
        assertTrue(compact.contains("BotDescriptor.BotType.FARMING_QUALITY,\"quality\""));
        assertTrue(compact.contains("BotDescriptor.BotType.BATTLE,\"battle\""));
        assertTrue(compact.contains("BotDescriptor.BotType.BUILD,\"build\""));
        assertTrue(compact.contains("BotDescriptor.BotType.TOOLS,\"tools\""));
        assertFalse(compact.contains("\"livestock\""));
    }

    @Test
    void animalsCategoryIconsMatchUpstreamResourceStructure() throws Exception {
        Path icons = Path.of("resources/src/nurgling/bots/icons/animals");
        String[] relative = {
                "d.res/image/image_0.data",
                "d.res/image/image_0.png",
                "d.res/meta",
                "h.res/image/image_0.data",
                "h.res/image/image_0.png",
                "h.res/meta",
                "u.res/image/image_0.data",
                "u.res/image/image_0.png",
                "u.res/meta"
        };
        assertEquals(9, relative.length);
        for (String name : relative) {
            assertTrue(Files.isRegularFile(icons.resolve(name)), "missing " + name);
        }
        assertTrue(Files.isDirectory(icons.resolve("u.res")));
        assertTrue(Files.isDirectory(icons.resolve("d.res")));
        assertTrue(Files.isDirectory(icons.resolve("h.res")));
        assertTrue(Files.isRegularFile(icons.resolve("u.res/meta")));
        assertTrue(Files.isRegularFile(icons.resolve("d.res/meta")));
        assertTrue(Files.isRegularFile(icons.resolve("h.res/meta")));
        assertFalse(Files.isRegularFile(icons.resolve("u.res/tooltip/tooltip_0.data")),
                "do not invent extra tooltip files");
        try (java.util.stream.Stream<Path> walk = Files.walk(icons)) {
            long files = walk.filter(Files::isRegularFile).count();
            assertEquals(9, files, "animals icons must be exactly the nine u/d/h resource files");
        }
    }
}
