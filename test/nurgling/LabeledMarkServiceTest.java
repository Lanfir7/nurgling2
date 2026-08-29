package nurgling;

import haven.Coord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void removeAllForageMarksLeavesNonForageMarks() {
        Path file = tempDir.resolve("labeled-marks.json");
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        try {
            service.addForageMark("q50", "Blueberries", 1L, new Coord(10, 20), null);
            service.addForageMark("q60", "Morels", 1L, new Coord(30, 40), null);
            service.addLabeledMark("q80", "Iron Ore", 80, 1L, new Coord(50, 60), null);

            assertEquals(3, service.getAllMarks().size());
            assertEquals(2, service.removeAllForageMarks());
            assertEquals(0, service.removeAllForageMarks());
            assertEquals(1, service.getAllMarks().size());
            String remainingId = service.getAllMarks().iterator().next().getLocationId();
            assertFalse(nurgling.tools.ForageMarkerLogic.isForageId(remainingId));
            assertEquals("Iron Ore", service.getAllMarks().iterator().next().resourceType);
            assertTrue(remainingId.startsWith("labeled_"));
        } finally {
            service.dispose();
        }
    }
}
