package nurgling.navigation;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkNavManagerInitializationTest {
    private NConfig previousConfig;
    private ChunkNavManager manager;

    @BeforeEach
    void installConfig() {
        previousConfig = NConfig.current;
        NConfig.current = new NConfig();
        manager = new ChunkNavManager();
    }

    @AfterEach
    void restoreConfig() {
        if (manager != null) {
            manager.shutdown();
        }
        NConfig.current = previousConfig;
    }

    @Test
    void everyInitializationTurnsOffManualChunkNavRecording() {
        NConfig.set(NConfig.Key.chunkNavOverlay, true);
        assertTrue(Boolean.TRUE.equals(NConfig.get(NConfig.Key.chunkNavOverlay)));

        manager.initialize("chunknav-login-reset-test");

        assertFalse(Boolean.TRUE.equals(NConfig.get(NConfig.Key.chunkNavOverlay)));

        NConfig.set(NConfig.Key.chunkNavOverlay, true);
        manager.initialize("chunknav-login-reset-test");

        assertFalse(Boolean.TRUE.equals(NConfig.get(NConfig.Key.chunkNavOverlay)));
    }
}
