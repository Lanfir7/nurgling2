package nurgling;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackagingTest {
    @Test
    void releaseResourceJarContainsMinimapMineralButtons() throws Exception {
        try (JarFile resources = new JarFile(Path.of("release", "nurgling-res.jar").toFile())) {
            for (String button : List.of("gems", "stone", "ores")) {
                for (String state : List.of("u", "d", "h", "dh")) {
                    String entry = "res/nurgling/hud/buttons/" + button + "/" + state + ".res";
                    assertTrue(resources.getEntry(entry) != null, "missing " + entry);
                }
            }
        }
    }
}
