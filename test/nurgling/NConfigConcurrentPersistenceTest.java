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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void repeatedScalarSetDoesNotScheduleSaveButMutatedValueDoes() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Files.writeString(target, "{\"showGrid\":false}");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();
        config.write();

        NConfig.set(NConfig.Key.showGrid, false);
        assertFalse(config.isUpdated());

        @SuppressWarnings("unchecked")
        ArrayList<Object> homes = (ArrayList<Object>) NConfig.get(NConfig.Key.homeTerritories);
        homes.add("new home");
        NConfig.set(NConfig.Key.homeTerritories, homes);
        assertTrue(config.isUpdated());
    }

    @Test
    void unloadedConfigCannotOverwriteAnExistingFileOnJvmShutdown() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        String original = "{\"preserveUserSetting\":true}";
        Files.writeString(target, original);
        Path appdata = tempDir.resolve("isolated-appdata");
        Path home = tempDir.resolve("isolated-home");
        Files.createDirectories(appdata);
        Files.createDirectories(home);
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        ProcessBuilder command = new ProcessBuilder(java.toString(),
                "-Duser.home=" + home,
                "-Djava.util.prefs.PreferencesFactory=haven.InMemoryPreferencesFactory",
                "-cp", System.getProperty("java.class.path"),
                UnloadedNConfigFixtureMain.class.getName(), target.toString());
        command.environment().put("APPDATA", appdata.toString());
        command.environment().put("LOCALAPPDATA", appdata.toString());
        command.environment().put("USERPROFILE", home.toString());
        command.redirectErrorStream(true);
        Process child = command.start();
        assertTrue(child.waitFor(10, TimeUnit.SECONDS), "fixture JVM must exit");
        String output = new String(child.getInputStream().readAllBytes());
        assertEquals(0, child.exitValue(), output);
        assertEquals(original, Files.readString(target),
                "a defaults-only NConfig must never persist from a shutdown hook");
    }

    @Test
    void tickSaveRequiresACompletedRead() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        String original = "{\"showGrid\":false,\"preserveUserSetting\":true}";
        Files.writeString(target, original);
        NConfig config = new NConfig();
        config.path = target.toString();
        NConfig.current = config;
        NConfig.set(NConfig.Key.showGrid, true);
        config.writeIfUpdated();
        NConfig.awaitQueuedWrites();
        assertEquals(original, Files.readString(target));

        config.read();
        NConfig.set(NConfig.Key.showGrid, true);
        config.writeIfUpdated();
        NConfig.awaitQueuedWrites();
        assertTrue(new JSONObject(Files.readString(target)).getBoolean("showGrid"));
        assertTrue(new JSONObject(Files.readString(target)).getBoolean("preserveUserSetting"));
    }

    @Test
    void explicitWriteCannotReplaceExistingFileBeforeRead() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        String original = "{\"showGrid\":false,\"preserveUserSetting\":true}";
        Files.writeString(target, original);
        NConfig config = new NConfig();
        config.path = target.toString();
        NConfig.current = config;
        NConfig.set(NConfig.Key.showGrid, true);

        config.write();

        assertEquals(original, Files.readString(target));
        assertTrue(config.isUpdated());
    }

    @Test
    void failedReadCannotReplaceCorruptPrimaryOrBackupWithDefaults() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Path backup = target.resolveSibling("nconfig.json.bak");
        Files.writeString(target, "{broken-primary");
        Files.writeString(backup, "{broken-backup");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();
        NConfig.set(NConfig.Key.showGrid, true);

        config.write();
        NConfig.flushPendingWrites();

        assertEquals("{broken-primary", Files.readString(target));
        assertEquals("{broken-backup", Files.readString(backup));
    }

    @Test
    void explicitWriteCreatesConfigAfterReadingMissingFile() throws Exception {
        Path target = tempDir.resolve("fresh-nconfig.json");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();
        NConfig.set(NConfig.Key.showGrid, true);

        config.write();

        assertTrue(new JSONObject(Files.readString(target)).getBoolean("showGrid"));
        assertFalse(config.isUpdated());
    }

    @Test
    void concurrentTickSaveCoalescesAndLeavesUiSettingsResponsive() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Files.writeString(target, "{\"showGrid\":false}");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();
        config.write();

        CountDownLatch enteredSnapshot = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        AtomicInteger snapshots = new AtomicInteger();
        JConf blockingValue = () -> {
            if (snapshots.getAndIncrement() == 0) {
                enteredSnapshot.countDown();
                try {
                    if (!releaseSnapshot.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("snapshot was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return new JSONObject().put("type", "test");
        };
        ArrayList<JConf> values = new ArrayList<>();
        values.add(blockingValue);
        NConfig.set(NConfig.Key.animalrad, values);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            config.writeIfUpdated();
            assertTrue(enteredSnapshot.await(10, TimeUnit.SECONDS));
            Future<?> uiWork = executor.submit(() -> {
                for (int i = 0; i < 100; i++)
                    config.writeIfUpdated();
                NConfig.set(NConfig.Key.showView, true);
                NConfig.get(NConfig.Key.showView);
            });
            uiWork.get(10, TimeUnit.SECONDS);
            releaseSnapshot.countDown();
            NConfig.flushPendingWrites();
            assertFalse(config.isUpdated());
            assertTrue(snapshots.get() >= 2, "stable snapshots need two serializations");
            assertTrue(new JSONObject(Files.readString(target)).getBoolean("showView"));
        } finally {
            releaseSnapshot.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void explicitWriteWaitsForAsyncSaveAndPersistsLatestRevision() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Files.writeString(target, "{\"showGrid\":false,\"showView\":false}");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();

        CountDownLatch enteredSnapshot = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        JConf blockingValue = () -> {
            enteredSnapshot.countDown();
            try {
                if (!releaseSnapshot.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("snapshot was not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return new JSONObject().put("type", "test");
        };
        ArrayList<JConf> values = new ArrayList<>();
        values.add(blockingValue);
        NConfig.set(NConfig.Key.animalrad, values);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            config.writeIfUpdated();
            assertTrue(enteredSnapshot.await(10, TimeUnit.SECONDS));
            NConfig.set(NConfig.Key.showView, true);
            Future<?> explicit = executor.submit(config::write);
            releaseSnapshot.countDown();
            explicit.get(10, TimeUnit.SECONDS);
            NConfig.flushPendingWrites();
            assertFalse(config.isUpdated());
            assertTrue(new JSONObject(Files.readString(target)).getBoolean("showView"));
        } finally {
            releaseSnapshot.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void repeatedTickCallsQueueOnlyOneSaveForAnInstance() throws Exception {
        Path target = tempDir.resolve("nconfig.json");
        Files.writeString(target, "{\"showGrid\":false}");
        NConfig config = new NConfig();
        config.path = target.toString();
        config.read();
        CountDownLatch enteredSnapshot = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        AtomicInteger serializations = new AtomicInteger();
        JConf blockingValue = () -> {
            if (serializations.getAndIncrement() == 0) {
                enteredSnapshot.countDown();
                try {
                    if (!releaseSnapshot.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("snapshot was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return new JSONObject().put("type", "test");
        };
        ArrayList<JConf> values = new ArrayList<>();
        values.add(blockingValue);
        NConfig.set(NConfig.Key.animalrad, values);

        try {
            config.writeIfUpdated();
            assertTrue(enteredSnapshot.await(10, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++)
                config.writeIfUpdated();
        } finally {
            releaseSnapshot.countDown();
        }
        NConfig.awaitQueuedWrites();
        assertEquals(2, serializations.get(), "one save needs exactly two stable snapshots");
        assertFalse(config.isUpdated());
    }

    @Test
    void failedAsyncSaveStaysDirtyAndRetriesAfterBackoff() throws Exception {
        AtomicLong clock = new AtomicLong(1);
        NConfig config = new NConfig() {
            @Override long saveClockNanos() { return clock.get(); }
        };
        Path target = tempDir.resolve("nconfig.json");
        Files.writeString(target, "{\"showGrid\":false}");
        config.path = target.toString();
        config.read();
        AtomicInteger serializations = new AtomicInteger();
        JConf value = () -> {
            serializations.incrementAndGet();
            return new JSONObject().put("type", "test");
        };
        ArrayList<JConf> values = new ArrayList<>();
        values.add(value);
        NConfig.set(NConfig.Key.animalrad, values);
        config.path = tempDir.toString(); // Writing a JSON file over a directory fails.

        config.writeIfUpdated();
        NConfig.awaitQueuedWrites();
        assertTrue(config.isUpdated());
        int firstSaveSerializations = serializations.get();
        assertEquals(2, firstSaveSerializations);
        config.writeIfUpdated();
        NConfig.awaitQueuedWrites();
        assertEquals(firstSaveSerializations, serializations.get(), "failure retries are throttled");

        config.path = target.toString();
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        config.writeIfUpdated();
        NConfig.awaitQueuedWrites();
        assertFalse(config.isUpdated());
        assertTrue(new JSONObject(Files.readString(target)).has("animalrad"));
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
