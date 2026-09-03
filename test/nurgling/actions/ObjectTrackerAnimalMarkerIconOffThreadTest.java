package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectTrackerAnimalMarkerIconOffThreadTest {
    @Test
    void saveAnimalMarkerPlacesLocallyBeforeIconLoadOnWorker() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/actions/ObjectTracker.java"));
        int saveAt = src.indexOf("private void saveAnimalMarkerToDb(Gob gob, String displayName, String animalType, int attempt)");
        int loadAt = src.indexOf("private static BufferedImage loadAnimalIcon(Gob gob)");
        assertTrue(saveAt >= 0 && loadAt > saveAt, "saveAnimalMarkerToDb must precede loadAnimalIcon");
        String save = src.substring(saveAt, loadAt);

        int addLocalAt = save.indexOf("addAnimalMarkerLocal");
        assertTrue(addLocalAt >= 0, "new markers must still be placed immediately");

        int acquireAt = save.indexOf("tryAcquire");
        assertTrue(acquireAt >= 0 && acquireAt < addLocalAt,
                "in-flight must be acquired before addAnimalMarkerLocal so draw cannot steal the gob loader");
        assertTrue(save.contains("submitAcquired"),
                "gob icon load must be submitted after the local mark exists");

        int loadIconCall = indexOfUncommented(save, "loadAnimalIcon(");
        assertTrue(loadIconCall < 0 || loadIconCall > addLocalAt,
                "loadAnimalIcon must not run on the caller thread before addAnimalMarkerLocal");

        int workerAt = save.indexOf("getAnimalMarkerWorker()");
        int getPathAt = indexOfUncommented(save, "getAnimalIconPath(");
        assertTrue(getPathAt < 0 || (workerAt >= 0 && getPathAt > workerAt),
                "getAnimalIconPath must run on the worker, not before the DB insert is queued");

        assertTrue(save.contains("getAnimalMarkerWorker()"),
                "icon load / DB insert must stay on AnimalMarkerWorker");
        assertTrue(save.contains("AnimalMarkerIconLoad") || save.contains("updateAnimalMarkerIcon"),
                "icon must be filled in on the worker after the local mark is placed");
        assertFalse(save.contains("SettingsWindow"),
                "must not change claim-skip placement rules");
    }

    private static int indexOfUncommented(String src, String token) {
        int at = 0;
        while (true) {
            int found = src.indexOf(token, at);
            if (found < 0) {
                return -1;
            }
            int lineStart = src.lastIndexOf('\n', found) + 1;
            int lineEnd = src.indexOf('\n', found);
            if (lineEnd < 0) {
                lineEnd = src.length();
            }
            String line = src.substring(lineStart, lineEnd).trim();
            if (!line.startsWith("//")) {
                return found;
            }
            at = found + token.length();
        }
    }
}
