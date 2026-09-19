package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeContainersTest {
    @Test
    void missingContainerWalksBackIntoTheParentZoneToReloadGobs() throws Exception {
        String src = new String(Files.readAllBytes(Paths.get("src/nurgling/actions/FreeContainers.java")),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("navigateToArea(container.parent, true)"), src);
    }
}
