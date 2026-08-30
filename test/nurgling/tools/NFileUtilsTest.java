package nurgling.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class NFileUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void decodeInvalidPrimaryFallsBackToAndRestoresValidBackup() throws Exception {
        Path primary = tempDir.resolve("icons.conf");
        Path backup = tempDir.resolve("icons.conf.bak");
        byte[] signature = {1, 2};
        byte[] truncated = {1, 2, 3};
        byte[] valid = {1, 2, 3, 4};
        Files.write(primary, truncated);
        Files.write(backup, valid);

        byte[] loaded = NFileUtils.readBytesWithBackupFallback(
                primary.toString(), signature, raw -> raw.length == valid.length);

        assertArrayEquals(valid, loaded);
        assertArrayEquals(valid, Files.readAllBytes(primary));
    }
}
