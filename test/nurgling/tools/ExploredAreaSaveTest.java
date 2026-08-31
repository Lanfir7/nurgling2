package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ExploredAreaSaveTest {
    @TempDir
    Path tempDir;

    @Test
    void mergeSaveDoesNotDeadlockOnItsOwnLockFile() {
        ExploredArea explored = new ExploredArea(null);
        Path target = tempDir.resolve("explored.json");

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> explored.mergeAndSaveToFile(target.toString()));
    }

    @Test
    void mergeSavePreservesExistingExploration() throws Exception {
        Path target = tempDir.resolve("explored-existing.json");
        ExploredArea existing = new ExploredArea(null);
        existing.updateExploredTiles(Coord.z, Coord.of(1, 1), 7);
        Files.write(target, existing.toJson().toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        new ExploredArea(null).mergeAndSaveToFile(target.toString());

        assertTrue(new String(Files.readAllBytes(target), java.nio.charset.StandardCharsets.UTF_8)
                .contains("\"seg\":7"));
    }
}
