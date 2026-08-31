package nurgling;

import nurgling.conf.JConf;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NConfigConcurrentPersistenceTest {
    @TempDir
    Path tempDir;

    private final NConfig previous = NConfig.current;

    @AfterEach
    void restoreGlobalConfig() {
        NConfig.current = previous;
    }

    @Test
    void saveKeepsASettingChangedByAnotherClientAfterThisClientLoaded() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Files.writeString(target, "{\"showGrid\":false,\"showView\":false}");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();

        NConfig.set(NConfig.Key.showGrid, true);
        JSONObject external = new JSONObject(Files.readString(target));
        external.put("showView", true);
        Files.writeString(target, external.toString());

        config.write();

        JSONObject persisted = new JSONObject(Files.readString(target));
        assertTrue(persisted.getBoolean("showGrid"));
        assertTrue(persisted.getBoolean("showView"));
    }

    @Test
    void settingChangedWhileSaveWaitsForDiskRemainsDirty() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Files.writeString(target, "{\"showGrid\":false,\"showView\":false}");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();
        NConfig.set(NConfig.Key.showGrid, true);
        Path lockPath = target.resolveSibling(target.getFileName() + ".lock");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> save;

        try {
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                save = executor.submit(config::write);
                Thread.sleep(100);
                assertFalse(save.isDone(), "save must be waiting for the file lock");
                NConfig.set(NConfig.Key.showView, true);
            }

            save.get(10, TimeUnit.SECONDS);
            assertTrue(config.isUpdated(), "the change made during I/O still needs a save");
            config.write();
            assertFalse(config.isUpdated());
        } finally {
            executor.shutdownNow();
        }

        JSONObject persisted = new JSONObject(Files.readString(target));
        assertTrue(persisted.getBoolean("showGrid"));
        assertTrue(persisted.getBoolean("showView"));
    }

    @Test
    void transientMutableSnapshotFailureIsRetriedBeforeWriting() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Files.writeString(target, "{\"showGrid\":false}");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();
        AtomicInteger attempts = new AtomicInteger();
        JConf changingValue = () -> {
            if (attempts.getAndIncrement() == 0) {
                throw new ConcurrentModificationException("simulated concurrent mutation");
            }
            return new JSONObject().put("type", "stable").put("value", 1);
        };
        ArrayList<JConf> values = new ArrayList<>();
        values.add(changingValue);
        NConfig.set(NConfig.Key.animalrad, values);

        config.write();

        assertFalse(config.isUpdated());
        assertTrue(attempts.get() >= 3, "two matching successful snapshots must be observed");
        assertTrue(new JSONObject(Files.readString(target)).has("animalrad"));
    }
}
