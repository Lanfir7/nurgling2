package nurgling.util;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Thread-safe and process-safe JSON file writer.
 * Uses file locking to prevent concurrent writes from multiple client instances,
 * and atomic temp-file + rename to prevent corruption.
 * <p>
 * Java 8 compatible.
 */
public class SafeJsonWriter {

    /**
     * Atomically writes a complete JSON object to the given file path with inter-process locking.
     * Use this for files where the entire content is owned by one writer (areas, scenarios, presets, etc.).
     *
     * @param filePath target JSON file path
     * @param json     the complete JSON to write
     */
    public static void writeAtomic(String filePath, JSONObject json) throws IOException {
        writeAtomicString(filePath, json.toString(2));
    }

    /**
     * Atomically writes a raw string to the given file path with inter-process locking.
     * Useful for files that use JSONArray as root or non-standard formatting.
     *
     * @param filePath target file path
     * @param content  the string content to write
     */
    public static void writeAtomicString(String filePath, String content) throws IOException {
        Path target = Paths.get(filePath);
        Path lockFile = Paths.get(filePath + ".lock");
        Path tempFile = Paths.get(filePath + ".tmp");

        // Ensure parent directory exists
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {

            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
            atomicMove(tempFile, target);
        }
    }

    /**
     * Reads the current JSON object from a file on disk. Returns an empty JSONObject if the file
     * does not exist or cannot be parsed.
     *
     * @param filePath path to the JSON file
     * @return parsed JSONObject, or empty JSONObject on failure
     */
    public static JSONObject readCurrent(String filePath) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(filePath));
            String content = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!content.isEmpty() && content.startsWith("{")) {
                return new JSONObject(content);
            }
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    /**
     * Read-merge-write: acquires a file lock, reads the current file contents from disk,
     * overlays the provided dirty keys on top, and writes the merged result atomically.
     * <p>
     * This is the key method for multi-instance safety: each client only overwrites
     * the keys it actually changed, preserving changes made by other clients.
     *
     * @param filePath  target JSON file path
     * @param dirtyData JSONObject containing only the keys that were modified
     */
    public static void mergeAndWrite(String filePath, JSONObject dirtyData) throws IOException {
        Path lockFilePath = Paths.get(filePath + ".lock");
        Path tempFile = Paths.get(filePath + ".tmp");
        Path target = Paths.get(filePath);

        // Ensure parent directory exists
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (FileChannel channel = FileChannel.open(lockFilePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {

            // Read current state from disk
            JSONObject current = readCurrent(filePath);
            // Merge dirty keys on top of current state
            for (String key : dirtyData.keySet()) {
                current.put(key, dirtyData.get(key));
            }
            // Atomic write
            Files.write(tempFile, current.toString(2).getBytes(StandardCharsets.UTF_8));
            atomicMove(tempFile, target);
        }
    }

    /**
     * Moves a temp file to the target path, attempting atomic move first,
     * falling back to regular replace if atomic is not supported.
     */
    private static void atomicMove(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
