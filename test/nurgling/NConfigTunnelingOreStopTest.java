package nurgling;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NConfigTunnelingOreStopTest {
    @TempDir Path temp;
    private final NConfig previous = NConfig.current;

    @AfterEach void restore() { NConfig.current = previous; }

    @Test
    void missingOreSettingDefaultsOffAndNewChoiceRoundTripsInTemporaryConfig() throws Exception {
        Path file = temp.resolve("nconfig.json");
        Files.writeString(file, new JSONObject().put("gridbox", true).toString());
        NConfig current = new NConfig();
        current.path = file.toString();
        NConfig.current = current;
        current.read();
        assertEquals("", NConfig.get(NConfig.Key.tunnelingOreStop));

        Map<String, Object> selected = new HashMap<>();
        selected.put("enabled", true);
        selected.put("ores", Arrays.asList("hematite"));
        NConfig.set(NConfig.Key.tunnelingOreStop, selected);
        current.write();
        NConfig loaded = new NConfig();
        loaded.path = file.toString();
        NConfig.current = loaded;
        loaded.read();
        assertEquals(selected, NConfig.get(NConfig.Key.tunnelingOreStop));
        assertEquals(true, NConfig.get(NConfig.Key.gridbox));
    }
}
