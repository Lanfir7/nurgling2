package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NMiniMapAnimalIconDrawTest {
    @Test
    void animalDrawPathDoesNotLoadIconsOnUiThread() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/widgets/NMiniMap.java"));
        int drawAt = src.indexOf("private void drawLabeledMarks");
        int nextAt = src.indexOf("private void drawterrainname");
        assertTrue(drawAt >= 0 && nextAt > drawAt, "drawLabeledMarks block must be present");
        String draw = src.substring(drawAt, nextAt);

        int lazyAt = draw.indexOf("iconTex == null && isAnimalMark(mark)");
        assertTrue(lazyAt >= 0, "animal placeholder path must still exist when iconTex is null");
        String lazy = draw.substring(lazyAt);

        assertFalse(lazy.contains("loadAnimalIconFromPath"),
                "draw must not call loadAnimalIconFromPath");
        assertFalse(lazy.contains("loadIconFromIconConf"),
                "draw must not call loadIconFromIconConf");
        assertFalse(lazy.contains("markerIconImage"),
                "draw must not call markerIconImage");
        assertFalse(lazy.contains("loadIconFromResourcePath"),
                "draw must not call loadIconFromResourcePath");
        assertFalse(lazy.contains("Resource.loadsimg"),
                "draw must not load kritter fallback on the UI thread");

        assertTrue(lazy.contains("AnimalMarkerIconLoad") || lazy.contains("getAnimalMarkerWorker"),
                "null animal iconTex must enqueue one worker job instead of loading");
        assertTrue(src.contains("g.fellipse"),
                "placeholder circle drawing must remain");
    }
}
