package haven;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCacheLocalAreaVisibilityTest {
    @TempDir
    Path tempDir;

    private NConfig previousConfig;

    @BeforeEach
    void useDatabaseOffConfig() {
        previousConfig = NConfig.current;
        NConfig.current = new NConfig();
    }

    @AfterEach
    void restoreConfig() {
        NConfig.current = previousConfig;
    }

    @Test
    void locallyEnabledAreaStaysEnabledWhenLoadedWithoutDatabase() throws Exception {
        MCache cache = loadAreas(false);

        assertFalse(cache.areas.get(1).hide);
    }

    @Test
    void locallyDisabledAreaStaysDisabledWhenLoadedWithoutDatabase() throws Exception {
        MCache cache = loadAreas(true);

        assertTrue(cache.areas.get(1).hide);
    }

    private MCache loadAreas(boolean hidden) throws Exception {
        Path areasFile = tempDir.resolve("areas.nurgling.json");
        String json = "{\"areas\":[{\"name\":\"Local area\",\"id\":1,\"hide\":"
                + hidden + ",\"space\":[]}]}";
        Files.write(areasFile, json.getBytes(StandardCharsets.UTF_8));

        MCache cache = new MCache(null) {
            @Override
            public String getAreasPath() {
                return areasFile.toString();
            }
        };
        cache.loadAreasIfNeeded();
        return cache;
    }
}
