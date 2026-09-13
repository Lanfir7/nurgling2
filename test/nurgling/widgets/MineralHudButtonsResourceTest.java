package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineralHudButtonsResourceTest {

    @Test
    void gemsAndStoneButtonTreesExist() throws Exception {
        assertButtonTree("resources/src/nurgling/hud/buttons/gems");
        assertButtonTree("resources/src/nurgling/hud/buttons/stone");
    }

    @Test
    void oresAndQuickBarragePngsExist() throws Exception {
        String[][] ores = {
                {"resources/src/nurgling/hud/buttons/ores/d.res/image/image_0.png", "b93b7a2ab0c31023340995242235e0cd561bd3a2"},
                {"resources/src/nurgling/hud/buttons/ores/dh.res/image/image_0.png", "315fd5b464c6881b7200b8a8508ccf57183d9e07"},
                {"resources/src/nurgling/hud/buttons/ores/h.res/image/image_0.png", "649b42c6f036fa3cc9d0ab593dbcc8498355abaf"},
                {"resources/src/nurgling/hud/buttons/ores/u.res/image/image_0.png", "20d48257aad842554df150c85254c39c251d161c"}
        };
        String[] barrage = {
                "resources/src/nurgling/bots/icons/quickbarrage/d.res/image/image_0.png",
                "resources/src/nurgling/bots/icons/quickbarrage/h.res/image/image_0.png",
                "resources/src/nurgling/bots/icons/quickbarrage/u.res/image/image_0.png"
        };
        for (String[] ore : ores) {
            Path file = Path.of(ore[0]);
            assertPng(file);
            assertEquals(ore[1], gitBlobSha1(file), ore[0]);
        }
        for (String path : barrage) {
            assertPng(Path.of(path));
        }
    }

    private static void assertButtonTree(String root) throws Exception {
        Path base = Path.of(root);
        String[] relative = {
                "d.res/image/image_0.data",
                "d.res/image/image_0.png",
                "d.res/meta",
                "dh.res/image/image_0.data",
                "dh.res/image/image_0.png",
                "dh.res/meta",
                "h.res/image/image_0.data",
                "h.res/image/image_0.png",
                "h.res/meta",
                "u.res/image/image_0.data",
                "u.res/image/image_0.png",
                "u.res/meta",
                "u.res/tooltip/tooltip_0.data"
        };
        for (String name : relative) {
            Path file = base.resolve(name);
            assertTrue(Files.isRegularFile(file), "missing " + file);
            if (name.endsWith(".png")) {
                assertPng(file);
            }
        }
    }

    private static void assertPng(Path file) {
        assertTrue(Files.isRegularFile(file), "missing " + file);
        try {
            byte[] bytes = Files.readAllBytes(file);
            assertEquals(0x89, bytes[0] & 0xFF);
            assertEquals('P', bytes[1]);
            assertEquals('N', bytes[2]);
            assertEquals('G', bytes[3]);
            assertTrue(bytes.length > 100, file + " too small");
        } catch (Exception e) {
            throw new AssertionError(file + ": " + e.getMessage(), e);
        }
    }

    private static String gitBlobSha1(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
        md.update(bytes);
        byte[] hash = md.digest();
        StringBuilder sb = new StringBuilder(40);
        for (byte b : hash)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
