package nurgling.tools;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * Atomic file write utilities to prevent corruption on crash/kill.
 *
 * Instead of truncating the target file and then writing (which leaves
 * an empty or partial file if the process dies mid-write), this writes
 * to a .tmp file first, then renames it over the target. A .bak copy
 * of the previous version is kept for recovery.
 */
public class NFileUtils {
    private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    @FunctionalInterface
    interface AtomicUpdate {
        byte[] apply(Path target, byte[] primary) throws IOException;
    }

    @FunctionalInterface
    private interface LockedOperation<T> {
        T run(Path target) throws IOException;
    }

    /**
     * Writes content to a file atomically using a temp-file-then-rename pattern.
     * Keeps a .bak backup of the previous version.
     *
     * @param targetPath path to the target file
     * @param content    the full content to write
     */
    public static void writeAtomically(String targetPath, String content) throws IOException {
        writeAtomically(targetPath, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Byte-oriented variant of {@link #writeAtomically(String, String)}, for files that
     * aren't text (the global icon settings are stored in the client's own binary format).
     *
     * @param targetPath path to the target file
     * @param content    the full content to write
     */
    public static void writeAtomically(String targetPath, byte[] content) throws IOException {
        withExclusiveLock(targetPath, target -> {
            writeAtomicallyLocked(target, content);
            return null;
        });
    }

    static byte[] updateAtomically(String targetPath, Predicate<byte[]> validator,
                                   AtomicUpdate update) throws IOException {
        return withExclusiveLock(targetPath, target -> {
            byte[] current = readBytesIfExists(target);
            boolean rotateExisting = validator.test(current);
            if (!rotateExisting) {
                Path backup = target.resolveSibling(target.getFileName() + ".bak");
                byte[] backupContent = readBytesIfExists(backup);
                if (validator.test(backupContent)) {
                    // Make the validated backup the current version before normal rotation. This
                    // prevents a corrupt primary from replacing the only usable backup.
                    restorePrimaryLocked(target, backupContent);
                    current = backupContent;
                    rotateExisting = true;
                } else {
                    current = null;
                }
            }
            byte[] updated = update.apply(target, current);
            writeAtomicallyLocked(target, updated, rotateExisting);
            return updated;
        });
    }

    private static <T> T withExclusiveLock(String targetPath, LockedOperation<T> operation) throws IOException {
        Path target = Path.of(targetPath).toAbsolutePath().normalize();
        ReentrantLock lock = JVM_LOCKS.computeIfAbsent(target, ignored -> new ReentrantLock());
        lock.lock();
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path lockPath = target.resolveSibling(target.getFileName() + ".lock");
            try (FileChannel lockChannel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = acquireFileLock(lockChannel)) {
                return operation.run(target);
            }
        } finally {
            lock.unlock();
        }
    }

    private static FileLock acquireFileLock(FileChannel channel) throws IOException {
        while (true) {
            try {
                return channel.lock();
            } catch (OverlappingFileLockException e) {
                // Another component in this JVM may have acquired the companion lock directly.
                // FileChannel.lock() reports that case instead of waiting, so retry as a waiter.
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException failure = new InterruptedIOException("interrupted while waiting for config lock");
                    failure.initCause(interrupted);
                    throw failure;
                }
            }
        }
    }

    private static byte[] readBytesIfExists(Path path) throws IOException {
        return Files.exists(path) ? Files.readAllBytes(path) : null;
    }

    private static void writeAtomicallyLocked(Path target, byte[] content) throws IOException {
        writeAtomicallyLocked(target, content, Files.exists(target));
    }

    private static void writeAtomicallyLocked(Path target, byte[] content,
                                              boolean rotateExisting) throws IOException {
        Path parent = target.getParent();
        Path temp = Files.createTempFile(parent, "." + target.getFileName() + "-", ".tmp");
        Path backup = target.resolveSibling(target.getFileName() + ".bak");
        Path backupTemp = null;
        try {
            // Step 1: Write and flush a private temp file (original untouched)
            Files.write(temp, content, StandardOpenOption.TRUNCATE_EXISTING);
            forceFile(temp);

            // Step 2: Atomically replace the backup with the current target
            if (rotateExisting && Files.exists(target)) {
                try {
                    backupTemp = Files.createTempFile(parent, "." + target.getFileName() + "-backup-", ".tmp");
                    Files.copy(target, backupTemp, StandardCopyOption.REPLACE_EXISTING);
                    forceFile(backupTemp);
                    moveReplacing(backupTemp, backup);
                    forceDirectory(parent);
                    backupTemp = null;
                } catch (IOException e) {
                    // Backup failure is non-fatal -- proceed with the save
                    System.err.println("[NFileUtils] Warning: could not create backup for " + target + ": " + e.getMessage());
                }
            } else if (!rotateExisting) {
                try {
                    backupTemp = Files.createTempFile(parent, "." + target.getFileName() + "-backup-", ".tmp");
                    Files.write(backupTemp, content, StandardOpenOption.TRUNCATE_EXISTING);
                    forceFile(backupTemp);
                    moveReplacing(backupTemp, backup);
                    forceDirectory(parent);
                    backupTemp = null;
                } catch (IOException e) {
                    // There is no validated previous version. Keep the new primary save usable
                    // even if establishing its initial recovery copy fails.
                    System.err.println("[NFileUtils] Warning: could not create backup for " + target + ": " + e.getMessage());
                }
            }

            // Step 3: Atomically replace the target
            moveReplacing(temp, target);
            forceDirectory(parent);
            temp = null;
        } finally {
            deleteTempQuietly(temp);
            deleteTempQuietly(backupTemp);
        }
    }

    private static void restorePrimaryLocked(Path target, byte[] content) throws IOException {
        Path parent = target.getParent();
        Path temp = Files.createTempFile(parent, "." + target.getFileName() + "-restore-", ".tmp");
        try {
            Files.write(temp, content, StandardOpenOption.TRUNCATE_EXISTING);
            forceFile(temp);
            moveReplacing(temp, target);
            forceDirectory(parent);
            temp = null;
        } finally {
            deleteTempQuietly(temp);
        }
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void forceDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // Windows/JDK providers commonly do not allow opening directories as channels.
            // File contents were already forced; atomic rename remains the supported fallback.
        }
    }

    private static void deleteTempQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            System.err.println("[NFileUtils] Warning: could not remove temporary file " + temp + ": " + e.getMessage());
        }
    }

    /**
     * Reads a file's content as a string. If the file is missing, empty, or
     * contains invalid content (doesn't start with '{' or '['), attempts to
     * read from the .bak file instead.
     *
     * @param targetPath path to the target file
     * @return the file content, or null if neither file nor backup is usable
     */
    public static String readWithBackupFallback(String targetPath) {
        String unlockedContent = decodeUtf8(readUnlocked(targetPath));
        if (isValidJsonContent(unlockedContent)) {
            return unlockedContent;
        }
        try {
            return withExclusiveLock(targetPath, target -> {
                Path backup = target.resolveSibling(target.getFileName() + ".bak");
                byte[] primaryBytes = readBytesIfExists(target);
                String content = decodeUtf8(primaryBytes);
                if (isValidJsonContent(content)) {
                    return content;
                }

                byte[] backupBytes = readBytesIfExists(backup);
                String backupContent = decodeUtf8(backupBytes);
                if (isValidJsonContent(backupContent)) {
                    System.err.println("[NFileUtils] Primary file corrupt or empty, trying backup: " + targetPath);
                    restoreFromBackupLocked(target, backupBytes, targetPath);
                    return backupContent;
                }

                if (content != null && !content.isEmpty()) {
                    System.err.println("[NFileUtils] Both primary and backup are corrupt: " + targetPath);
                }
                return null;
            });
        } catch (IOException e) {
            System.err.println("[NFileUtils] Warning: could not read " + targetPath + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Byte-oriented variant of {@link #readWithBackupFallback(String)}. A file counts as
     * usable when it starts with the given signature, so a truncated write falls through
     * to the .bak copy instead of being handed back as valid data.
     *
     * @param targetPath path to the target file
     * @param sig        expected leading bytes
     * @return the file content, or null if neither file nor backup is usable
     */
    public static byte[] readBytesWithBackupFallback(String targetPath, byte[] sig) {
        return readBytesWithBackupFallback(targetPath, sig, raw -> true);
    }

    /**
     * Byte-oriented fallback that also validates the complete payload. A matching signature is
     * not enough for structured binary data: a truncated file can still contain its full header.
     */
    public static byte[] readBytesWithBackupFallback(String targetPath, byte[] sig,
                                                     Predicate<byte[]> validator) {
        byte[] unlockedContent = readUnlocked(targetPath);
        if (isUsable(unlockedContent, sig, validator)) {
            return unlockedContent;
        }
        try {
            return withExclusiveLock(targetPath, target -> {
                Path backup = target.resolveSibling(target.getFileName() + ".bak");
                byte[] content = readBytesIfExists(target);
                if (isUsable(content, sig, validator)) {
                    return content;
                }

                byte[] backupContent = readBytesIfExists(backup);
                if (isUsable(backupContent, sig, validator)) {
                    System.err.println("[NFileUtils] Primary file corrupt or empty, trying backup: " + targetPath);
                    restoreFromBackupLocked(target, backupContent, targetPath);
                    return backupContent;
                }

                if (content != null && content.length > 0) {
                    System.err.println("[NFileUtils] Both primary and backup are corrupt: " + targetPath);
                }
                return null;
            });
        } catch (IOException e) {
            System.err.println("[NFileUtils] Warning: could not read " + targetPath + ": " + e.getMessage());
            return null;
        }
    }

    private static void restoreFromBackupLocked(Path target, byte[] backupContent, String displayPath) {
        try {
            restorePrimaryLocked(target, backupContent);
            System.err.println("[NFileUtils] Restored from backup: " + displayPath);
        } catch (IOException e) {
            System.err.println("[NFileUtils] Warning: could not restore backup to primary: " + e.getMessage());
        }
    }

    private static String decodeUtf8(byte[] content) {
        return content == null ? null : new String(content, StandardCharsets.UTF_8);
    }

    private static byte[] readUnlocked(String targetPath) {
        try {
            Path target = Path.of(targetPath).toAbsolutePath().normalize();
            return readBytesIfExists(target);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isUsable(byte[] content, byte[] sig, Predicate<byte[]> validator) {
        return hasPrefix(content, sig) && validator.test(content);
    }

    private static boolean hasPrefix(byte[] content, byte[] sig) {
        if (content == null || content.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if (content[i] != sig[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidJsonContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
