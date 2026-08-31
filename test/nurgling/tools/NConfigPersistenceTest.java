package nurgling.tools;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NConfigPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void mergeKeepsExternalChangesToKeysUntouchedLocally() {
        String baseline = "{\"local\":1,\"external\":1}";
        String local = "{\"local\":2,\"external\":1}";
        String latest = "{\"local\":1,\"external\":2,\"added\":3}";

        String merged = NConfigPersistence.mergeChangedKeys(baseline, local, latest);

        assertEquals(Map.of("local", 2, "external", 2, "added", 3), new JSONObject(merged).toMap());
    }

    @Test
    void mergeUsesLocalValueWhenBothClientsChangedTheSameKey() {
        String merged = NConfigPersistence.mergeChangedKeys(
                "{\"setting\":1}",
                "{\"setting\":2}",
                "{\"setting\":3}"
        );

        assertEquals(Map.of("setting", 2), new JSONObject(merged).toMap());
    }

    @Test
    void mergeDetectsNestedMutationAndDeletion() {
        String merged = NConfigPersistence.mergeChangedKeys(
                "{\"nested\":{\"value\":1},\"removed\":true,\"external\":1}",
                "{\"nested\":{\"value\":2},\"external\":1}",
                "{\"nested\":{\"value\":1},\"removed\":true,\"external\":2}"
        );

        assertEquals(
                Map.of("nested", Map.of("value", 2), "external", 2),
                new JSONObject(merged).toMap()
        );
    }

    @Test
    void mergedWritePreservesChangesFromTwoStaleClientSnapshots() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        String baseline = "{\"first\":1,\"second\":1}";
        Files.writeString(target, baseline);

        NConfigPersistence.mergeAndWrite(target.toString(), baseline, "{\"first\":2,\"second\":1}");
        NConfigPersistence.mergeAndWrite(target.toString(), baseline, "{\"first\":1,\"second\":2}");

        assertEquals(
                Map.of("first", 2, "second", 2),
                new JSONObject(Files.readString(target)).toMap()
        );
    }

    @Test
    void corruptPrimaryNeverReplacesTheOnlyValidBackup() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Path backup = target.resolveSibling(target.getFileName() + ".bak");
        String baseline = "{\"preserved\":1,\"local\":1}";
        Files.writeString(target, "corrupt-primary");
        Files.writeString(backup, baseline);

        NConfigPersistence.mergeAndWrite(
                target.toString(), baseline, "{\"preserved\":1,\"local\":2}"
        );

        assertEquals(
                Map.of("preserved", 1, "local", 2),
                new JSONObject(Files.readString(target)).toMap()
        );
        assertEquals(
                Map.of("preserved", 1, "local", 1),
                new JSONObject(Files.readString(backup)).toMap()
        );
    }

    @Test
    void corruptPrimaryWithoutBackupCreatesTwoValidCopies() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Path backup = target.resolveSibling(target.getFileName() + ".bak");
        Files.writeString(target, "corrupt-primary");

        NConfigPersistence.mergeAndWrite(
                target.toString(), "{\"local\":1}", "{\"local\":2}"
        );

        assertEquals(Map.of("local", 2), new JSONObject(Files.readString(target)).toMap());
        assertEquals(Map.of("local", 2), new JSONObject(Files.readString(backup)).toMap());
    }
}
