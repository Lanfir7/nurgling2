package nurgling.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NFileUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void decodeInvalidPrimaryFallsBackToAndRestoresValidBackup() throws Exception {
        Path primary = tempDir.resolve("icons.conf");
        Path backup = tempDir.resolve("icons.conf.bak");
        byte[] signature = {1, 2};
        byte[] truncated = {1, 2, 3};
        byte[] valid = {1, 2, 3, 4};
        Files.write(primary, truncated);
        Files.write(backup, valid);

        byte[] loaded = NFileUtils.readBytesWithBackupFallback(
                primary.toString(), signature, raw -> raw.length == valid.length);

        assertArrayEquals(valid, loaded);
        assertArrayEquals(valid, Files.readAllBytes(primary));
    }

    @Test
    void concurrentWritesToSameTargetDoNotStealEachOthersTemporaryFile() throws Exception {
        Path target = tempDir.resolve("shared.json");
        int writerCount = 12;
        int writesPerWriter = 20;
        Set<String> validPayloads = java.util.stream.IntStream.range(0, writerCount)
                .mapToObj(i -> "{\"writer\":" + i + "}")
                .collect(Collectors.toSet());
        ExecutorService executor = Executors.newFixedThreadPool(writerCount);
        CountDownLatch ready = new CountDownLatch(writerCount);
        CountDownLatch start = new CountDownLatch(1);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Future<?>> tasks = new ArrayList<>();

        try {
            for (int writer = 0; writer < writerCount; writer++) {
                String payload = "{\"writer\":" + writer + "}";
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < writesPerWriter; i++) {
                            NFileUtils.writeAtomically(target.toString(), payload);
                        }
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "writers did not become ready");
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(failures.isEmpty(), () -> "concurrent write failed: " + failures.peek());
        assertTrue(validPayloads.contains(Files.readString(target)), "primary must contain one complete payload");
        Path backup = target.resolveSibling(target.getFileName() + ".bak");
        assertTrue(validPayloads.contains(Files.readString(backup)), "backup must contain one complete payload");
    }

    @Test
    void staleLegacyTemporaryPathDoesNotBlockANewWrite() throws Exception {
        Path target = tempDir.resolve("config.json");
        Files.createDirectory(target.resolveSibling(target.getFileName() + ".tmp"));

        NFileUtils.writeAtomically(target.toString(), "{\"saved\":true}");

        assertEquals("{\"saved\":true}", Files.readString(target));
    }

    @Test
    void recoveryWaitsForWriterLockAndRechecksPrimaryBeforeRestoringBackup() throws Exception {
        Path target = tempDir.resolve("recovery.json");
        Path backup = target.resolveSibling(target.getFileName() + ".bak");
        Path lockPath = target.resolveSibling(target.getFileName() + ".lock");
        Files.writeString(target, "broken");
        Files.writeString(backup, "{\"source\":\"backup\"}");
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (FileChannel channel = FileChannel.open(lockPath,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            Future<String> read = executor.submit(() -> NFileUtils.readWithBackupFallback(target.toString()));
            Thread.sleep(100);
            assertTrue(!read.isDone(), "recovery must wait for the active writer transaction");
            Files.writeString(target, "{\"source\":\"writer\"}");
        } finally {
            executor.shutdown();
        }

        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals("{\"source\":\"writer\"}", Files.readString(target));
    }

    @Test
    void separateProcessesSerializeWritesToSameTarget() throws Exception {
        Path target = tempDir.resolve("shared-process.json");
        Path startGate = tempDir.resolve("start.gate");
        List<Process> processes = new ArrayList<>();
        List<Path> readyFiles = new ArrayList<>();
        List<Path> logs = new ArrayList<>();
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
        ).toString();

        List<String> failures = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                Path ready = tempDir.resolve("writer-" + i + ".ready");
                Path log = tempDir.resolve("writer-" + i + ".log");
                readyFiles.add(ready);
                logs.add(log);
                Process process = new ProcessBuilder(
                        javaExecutable,
                        "-cp",
                        System.getProperty("java.class.path"),
                        NFileUtilsProcessWriter.class.getName(),
                        target.toString(),
                        "process-" + i,
                        startGate.toString(),
                        ready.toString(),
                        "40"
                ).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                processes.add(process);
            }

            long readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (readyFiles.stream().anyMatch(path -> !Files.exists(path)) && System.nanoTime() < readyDeadline) {
                Thread.sleep(10);
            }
            assertTrue(readyFiles.stream().allMatch(Files::exists), "child writers did not become ready");
            Files.write(startGate, new byte[]{1});

            for (int i = 0; i < processes.size(); i++) {
                Process process = processes.get(i);
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    failures.add("child writer timed out");
                    continue;
                }
                if (process.exitValue() != 0) {
                    failures.add(Files.readString(logs.get(i), StandardCharsets.UTF_8));
                }
            }
        } finally {
            for (Process process : processes) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
        }
        assertEquals(java.util.Collections.emptyList(), failures, "child writers must all succeed");

        Set<String> processPayloads = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> "process-" + i)
                .collect(Collectors.toSet());
        assertTrue(processPayloads.contains(Files.readString(target)),
                "primary must contain one complete child-process payload");
        Path backup = target.resolveSibling(target.getFileName() + ".bak");
        assertTrue(processPayloads.contains(Files.readString(backup)),
                "backup must contain one complete child-process payload");
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertTrue(files.map(path -> path.getFileName().toString()).noneMatch(name ->
                            name.startsWith(".shared-process.json-") && name.endsWith(".tmp")),
                    "private temporary files must be cleaned up");
        }
    }
}
