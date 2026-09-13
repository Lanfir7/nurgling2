package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineralButtonResourcesTest {

    @Test
    void gemsAndStoneButtonTreesExist() throws Exception {
        assertButtonTree("resources/src/nurgling/hud/buttons/gems");
        assertButtonTree("resources/src/nurgling/hud/buttons/stone");
    }

    @Test
    void oresPngsExist() throws Exception {
        assertOresHash("d", "b93b7a2ab0c31023340995242235e0cd561bd3a2");
        assertOresHash("dh", "315fd5b464c6881b7200b8a8508ccf57183d9e07");
        assertOresHash("h", "649b42c6f036fa3cc9d0ab593dbcc8498355abaf");
        assertOresHash("u", "20d48257aad842554df150c85254c39c251d161c");
    }

    @Test
    void quickBarragePngsExist() throws Exception {
        for (String state : new String[] {"d", "h", "u"}) {
            assertPng(Path.of("resources/src/nurgling/bots/icons/quickbarrage", state + ".res", "image", "image_0.png"));
        }
    }

    private static void assertButtonTree(String root) throws Exception {
        Path base = Path.of(root);
        String[] states = {"u", "d", "h", "dh"};
        for (String state : states) {
            Path res = base.resolve(state + ".res");
            Path png = res.resolve("image").resolve("image_0.png");
            Path data = res.resolve("image").resolve("image_0.data");
            Path meta = res.resolve("meta");
            assertPng(png);
            assertTrue(Files.isRegularFile(data), "missing " + data);
            assertTrue(Files.isRegularFile(meta), "missing " + meta);
        }
        assertTrue(Files.isRegularFile(base.resolve("u.res").resolve("tooltip").resolve("tooltip_0.data")),
                "missing " + base + " u.res tooltip");
    }

    private static void assertOresHash(String state, String expected) throws Exception {
        Path file = Path.of("resources/src/nurgling/hud/buttons/ores", state + ".res", "image", "image_0.png");
        assertPng(file);
        assertEquals(expected, gitBlobSha1(file), file.toString());
    }

    private static void assertPng(Path file) throws Exception {
        assertTrue(Files.isRegularFile(file), "missing " + file);
        byte[] bytes = Files.readAllBytes(file);
        assertTrue(bytes.length > 100, file + " too small");
        assertTrue(bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G',
                file + " is not a PNG");
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
