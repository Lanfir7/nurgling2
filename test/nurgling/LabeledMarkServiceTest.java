package nurgling;

import haven.Gob;
import nurgling.widgets.LabeledMinimapMark;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

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
    void inspectQualityReplacesEmptyAnimalMarkerWithQnAndKillTime() {
        Path file = tempDir.resolve("labeled-marks.json");
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        long gobId = 4242L;
        service.addAnimalMarkerLocal(gobId, "gfx/kritter/fox", "Fox", 1L, 10, 20, 1L, 0, 0, null);

        LabeledMinimapMark before = service.getMark("animal_" + gobId);
        assertNotNull(before);
        assertEquals("", before.label);
        assertNull(before.killedAtMs);

        long beforeMs = System.currentTimeMillis();
        service.applyAnimalMarkerQuality(gobId, 40, "Denis");

        LabeledMinimapMark after = service.getMark("animal_" + gobId);
        assertNotNull(after);
        assertEquals("animal_" + gobId, after.getLocationId());
        assertEquals("q40", after.label);
        assertNotNull(after.killedAtMs);
        assertTrue(after.killedAtMs >= beforeMs);
        assertEquals("Denis", after.killedBy);
        assertNotSame(before, after);
        assertEquals("", before.label);
    }

    @Test
    void animalQualityLabelMatchesDbMergeFormat() {
        assertEquals("q40", LabeledMarkService.animalQualityLabel(40));
        assertEquals("q40", LabeledMarkService.animalQualityLabel(40.4));
        assertEquals("q41", LabeledMarkService.animalQualityLabel(40.6));
    }

    @Test
    void nMapViewDeclaresApplyAnimalMarkerQuality() throws Exception {
        Method m = NMapView.class.getMethod("applyAnimalMarkerQuality", Gob.class, int.class);
        assertEquals(void.class, m.getReturnType());
        assertFalse(java.lang.reflect.Modifier.isStatic(m.getModifiers()));
    }

    @Test
    void inspectQualityDoesNotPersistAnimalMarksToFile() throws Exception {
        Path file = tempDir.resolve("labeled-marks.json");
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        service.addAnimalMarkerLocal(7L, "gfx/kritter/fox", "Fox", 1L, 10, 20, 1L, 0, 0, null);
        service.applyAnimalMarkerQuality(7L, 40, "Denis");
        service.dispose();

        String saved = Files.readString(file);
        assertFalse(saved.contains("animal_7"));
        assertFalse(saved.contains("q40"));
    }
}
