package nurgling;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NConfigGridDiagonalsTest {
    @TempDir Path temp;
    private final NConfig previous = NConfig.current;

    @AfterEach
    void restore() {
        NConfig.current = previous;
    }

    @Test
    void oldConfigLeavesDiagonalsOffAndRetainsWorldSettings() throws Exception {
        Path file = temp.resolve("nconfig.json");
        JSONObject edgeColor = new JSONObject().put("type", "Color")
                .put("red", 201).put("green", 50).put("blue", 10).put("alpha", 99);
        Files.writeString(file, new JSONObject().put("gridbox", true)
                .put("gridWallColor", edgeColor).put("showBB", true)
                .put("disableTileSmoothing", true).put("disableCloudShadows", true).toString());
        NConfig config = new NConfig();
        config.path = file.toString();
        NConfig.current = config;
        config.read();

        assertEquals(false, NConfig.get(NConfig.Key.gridDiagonals));
        assertEquals(true, NConfig.get(NConfig.Key.gridbox));
        assertEquals(new Color(201, 50, 10, 99), NConfig.getColor(NConfig.Key.gridWallColor, Color.BLACK));
        assertEquals(true, NConfig.get(NConfig.Key.showBB));
        assertEquals(true, NConfig.get(NConfig.Key.disableTileSmoothing));
        assertEquals(true, NConfig.get(NConfig.Key.disableCloudShadows));
        assertEquals(new Color(0, 190, 255, 217),
                NConfig.getColor(NConfig.Key.gridDiagonalColor, new Color(0, 190, 255, 217)));

        NConfig.set(NConfig.Key.gridDiagonals, true);
        NConfig.setColor(NConfig.Key.gridDiagonalColor, new Color(31, 67, 199, 123));
        config.write();

        NConfig loaded = new NConfig();
        loaded.path = file.toString();
        NConfig.current = loaded;
        loaded.read();
        assertEquals(true, NConfig.get(NConfig.Key.gridDiagonals));
        assertEquals(new Color(31, 67, 199, 123), NConfig.getColor(NConfig.Key.gridDiagonalColor, Color.BLACK));
        assertEquals(true, NConfig.get(NConfig.Key.gridbox));
        assertEquals(new Color(201, 50, 10, 99), NConfig.getColor(NConfig.Key.gridWallColor, Color.BLACK));
        assertEquals(true, NConfig.get(NConfig.Key.showBB));
        assertEquals(true, NConfig.get(NConfig.Key.disableTileSmoothing));
        assertEquals(true, NConfig.get(NConfig.Key.disableCloudShadows));
    }
}
