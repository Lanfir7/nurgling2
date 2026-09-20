package nurgling.craftatlas.quality;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static nurgling.craftatlas.quality.QualityWorkshopModel.Key.*;

class QualityWorkshopStoreTest {
    @TempDir Path directory;
    @Test void boardAndComparisonSurviveReopeningProfileFile() throws Exception {
        Path file = directory.resolve("workshop.json");
        QualityWorkshopModel model = new QualityWorkshopModel();
        model.setManual(BRICK, 212.5); model.setManual(DEXTERITY, 900); model.setManual(MASONRY, 900);
        model.snapshot(); model.setGenerations(3);
        QualityWorkshopStore.save(file, model);
        QualityWorkshopModel restored = QualityWorkshopStore.load(file);
        assertEquals(model.watched(), restored.watched());
        assertEquals(212.5, restored.manual(BRICK));
        assertEquals(model.value(POTTER_CLAY), restored.value(POTTER_CLAY));
        assertEquals(model.baseline(POTTER_CLAY), restored.baseline(POTTER_CLAY));
        assertEquals(3, restored.generations());
    }
    @Test void corruptOrAbsentFileGivesUsableDefaults() throws Exception {
        Path file = directory.resolve("broken.json");
        assertEquals(10, QualityWorkshopStore.load(file).manual(BRICK));
        Files.write(file, "{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(10, QualityWorkshopStore.load(file).manual(BRICK));
    }
}
