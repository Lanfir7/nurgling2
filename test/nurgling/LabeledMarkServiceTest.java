package nurgling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabeledMarkServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void repeatedDisposeDoesNotRewriteFinalSnapshot() throws Exception {
        Path file = tempDir.resolve("labeled-marks.json");
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());

        service.dispose();
        String firstSave = Files.readString(file);
        Thread.sleep(5);
        service.dispose();

        assertEquals(firstSave, Files.readString(file));
    }
}
