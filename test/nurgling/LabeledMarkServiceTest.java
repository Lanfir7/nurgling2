package nurgling;

import haven.Gob;
import haven.Coord;
import nurgling.widgets.LabeledMinimapMark;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LabeledMarkServiceTest {
    @TempDir
    Path tempDir;

    private static String validMarks(String type) {
        String marks = type == null ? "[]" : "[{\"locationId\":\"loaded_1\",\"label\":\"q42\",\"resourceType\":\""
            + type + "\",\"segmentId\":1,\"tileX\":2,\"tileY\":3,\"timestamp\":1}]";
        return "{\"version\":2,\"icons\":{},\"labeledMarks\":" + marks + ",\"lastSaved\":\"sentinel\"}";
    }

    @Test
    void cleanPrimaryCloseKeepsFileBytesAndTimestamp() throws Exception {
        Path file = tempDir.resolve("clean-marks.json");
        String original = validMarks("clean-type");
        Files.writeString(file, original);
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
        FileTime timestamp = Files.getLastModifiedTime(file);

        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        assertNotNull(service.getMark("loaded_1"));
        service.dispose();

        assertEquals(original, Files.readString(file));
        assertEquals(timestamp, Files.getLastModifiedTime(file));
    }

    @Test
    void pendingMarkIsDrainedAndSavedDuringClose() throws Exception {
        Path file = tempDir.resolve("pending-marks.json");
        Files.writeString(file, validMarks(null));
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        String id = service.addLabeledMarkAsync("q70", "pending-type", 7L,
            new Coord(10, 11), null);

        service.dispose();

        assertTrue(Files.readString(file).contains("\"locationId\":\"" + id + "\""));
    }

    @Test
    void failedExplicitSaveDoesNotMakeCloseLookClean() throws Exception {
        Path file = tempDir.resolve("failed-marks.json");
        String original = validMarks(null);
        Files.writeString(file, original);
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());

        Files.delete(file);
        Files.createDirectory(file);
        service.save(); // Atomic replacement cannot replace a directory.
        Files.delete(file);
        Files.writeString(file, original);
        service.dispose();

        assertNotEquals(original, Files.readString(file));
    }

    @Test
    void recoveredBackupAndLegacyPrimaryStillGetRewritten() throws Exception {
        Path recovered = tempDir.resolve("recovered-marks.json");
        Files.writeString(recovered, "not-json");
        Files.writeString(recovered.resolveSibling("recovered-marks.json.bak"), validMarks("backup-type"));
        LabeledMarkService fromBackup = new LabeledMarkService(null, "test", recovered.toString());
        assertNotNull(fromBackup.getMark("loaded_1"));
        fromBackup.dispose();
        assertNotEquals(validMarks("backup-type"), Files.readString(recovered));
        assertNotNull(new org.json.JSONObject(Files.readString(recovered)).getJSONArray("labeledMarks"));

        Path legacy = tempDir.resolve("legacy-marks.json");
        Files.writeString(legacy, "{\"version\":1,\"labeledMarks\":[],\"lastSaved\":\"sentinel\"}");
        LabeledMarkService fromLegacy = new LabeledMarkService(null, "test", legacy.toString());
        fromLegacy.dispose();
        assertEquals(2, new org.json.JSONObject(Files.readString(legacy)).getInt("version"));
    }

    @Test
    void partialAndDuplicatePrimaryAreNotMarkedClean() throws Exception {
        Path file = tempDir.resolve("partial-marks.json");
        String good = "{\"locationId\":\"one\",\"label\":\"q1\",\"resourceType\":\"partial-type\","
            + "\"segmentId\":1,\"tileX\":1,\"tileY\":1,\"timestamp\":1}";
        Files.writeString(file, "{\"version\":2,\"icons\":{},\"labeledMarks\":["
            + good + ",{\"label\":\"broken\"}," + good + "],\"lastSaved\":\"sentinel\"}");

        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        service.dispose();

        assertEquals(1, new org.json.JSONObject(Files.readString(file))
            .getJSONArray("labeledMarks").length());
    }

    @Test
    void lateSharedIconRegistrationKeepsCloseDirty() throws Exception {
        Path file = tempDir.resolve("late-icon-marks.json");
        Files.writeString(file, validMarks("late-icon-only-test"));
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        LabeledMinimapMark.registerIcon("late-icon-only-test",
            new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));

        service.dispose();

        assertTrue(new org.json.JSONObject(Files.readString(file)).getJSONObject("icons")
            .has("late-icon-only-test"));
    }

    @Test
    void mutationDuringBlockedSaveIsPersistedOnClose() throws Exception {
        Path file = tempDir.resolve("concurrent-marks.json");
        Files.writeString(file, validMarks(null));
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        AtomicReference<Throwable> saveFailure = new AtomicReference<>();
        Path companion = file.resolveSibling(file.getFileName() + ".lock");
        Thread explicitSave;
        try (FileChannel channel = FileChannel.open(companion,
                 StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock held = channel.lock()) {
            explicitSave = new Thread(() -> {
                try {
                    service.save();
                } catch (Throwable failure) {
                    saveFailure.set(failure);
                }
            }, "blocked-labeled-mark-save-test");
            explicitSave.start();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!inMethod(explicitSave, "acquireFileLock") && System.nanoTime() < deadline)
                Thread.sleep(5);
            assertTrue(inMethod(explicitSave, "acquireFileLock"), "save did not reach the file lock");
            service.addLabeledMark("q80", "concurrent-type", 80, 1L,
                new Coord(4, 5), null);
        }
        explicitSave.join(5_000);
        assertFalse(explicitSave.isAlive());
        assertNull(saveFailure.get());
        service.dispose();
        assertTrue(Files.readString(file).contains("concurrent-type"));
    }

    private static boolean inMethod(Thread thread, String method) {
        for (StackTraceElement frame : thread.getStackTrace()) {
            if (frame.getMethodName().equals(method)) return true;
        }
        return false;
    }

    @Test
    void asyncMiningMarkKeepsReturnedIdAndReachesDiskBeforeShutdown() throws Exception {
        Path file = tempDir.resolve("labeled-marks.json");
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        String id = service.addLabeledMarkAsync("q78", "Lead Glance", 42L,
            new Coord(11, 12), null, 40);
        BufferedImage icon = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        service.updateMarkIcon(id, icon); // May arrive before the queued mark is processed.

        long deadline = System.currentTimeMillis() + 5000;
        while ((!Files.exists(file) || service.getMark(id) == null)
                && System.currentTimeMillis() < deadline)
            Thread.sleep(10);

        assertNotNull(service.getMark(id));
        assertEquals(id, service.getMark(id).getLocationId());
        assertSame(icon, service.getMark(id).iconImage);
        assertTrue(Files.readString(file).contains("\"locationId\":\"" + id + "\""));

        BufferedImage laterIcon = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        service.updateMarkIcon(id, laterIcon);
        assertSame(laterIcon, service.getMark(id).iconImage);
        assertEquals(id, service.getMark(id).getLocationId());

        LabeledMarkService restored = new LabeledMarkService(null, "test", file.toString());
        assertNotNull(restored.getMark(id));
        restored.dispose();
        service.dispose();
    }

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

    @Test
    void staleDbMergeKeepsLocalInspectQualityAndKillTime() {
        Path file = tempDir.resolve("labeled-marks.json");
        LabeledMarkService service = new LabeledMarkService(null, "test", file.toString());
        long gobId = 9L;
        service.addAnimalMarkerLocal(gobId, "gfx/kritter/fox", "Fox", 1L, 10, 20, 1L, 0, 0, null);
        service.applyAnimalMarkerQuality(gobId, 40, "Denis");
        Long killedAt = service.getMark("animal_" + gobId).killedAtMs;

        nurgling.db.dao.AnimalMarkerDao.AnimalMarkerData stale =
            new nurgling.db.dao.AnimalMarkerDao.AnimalMarkerData(
                1, "test", gobId, "gfx/kritter/fox", "Fox", "",
                1L, 10, 20, 1L, 0, 0,
                null, null, null, null, null);
        service.mergeAnimalMarkersFromDb(java.util.Collections.singletonList(stale), null);

        LabeledMinimapMark after = service.getMark("animal_" + gobId);
        assertEquals("q40", after.label);
        assertEquals(killedAt, after.killedAtMs);
        assertEquals("Denis", after.killedBy);
    }
}
