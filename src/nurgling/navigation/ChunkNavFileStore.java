package nurgling.navigation;

import nurgling.profiles.ProfileManager;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static nurgling.navigation.ChunkNavConfig.*;

/**
 * Manages file storage for ChunkNav data.
 * Stores each chunk as a separate binary file in a directory.
 */
public class ChunkNavFileStore {

    private static final String CHUNK_EXTENSION = ".chunk";

    private final String genus;
    private final Path chunkDirectory;
    private final MutationState mutationState;
    private static final Map<Path, MutationState> mutationStates = new ConcurrentHashMap<>();

    private static final class MutationState {
        private long generation;
        private int activeWrites;

        synchronized void begin() { generation++; activeWrites++; }
        synchronized void end() { activeWrites--; generation++; }
        synchronized long stableGeneration() { return activeWrites == 0 ? generation : -1; }
    }

    private static MutationState mutationState(Path directory) {
        return mutationStates.computeIfAbsent(normalized(directory), ignored -> new MutationState());
    }

    public ChunkNavFileStore(String genus) {
        this.genus = genus;
        ProfileManager pm = new ProfileManager(genus);
        this.chunkDirectory = pm.getConfigPath(ChunkNavConfig.STORAGE_DIRNAME);
        this.mutationState = mutationState(chunkDirectory);
    }

    ChunkNavFileStore(String genus, Path chunkDirectory) {
        this.genus = genus;
        this.chunkDirectory = chunkDirectory;
        this.mutationState = mutationState(chunkDirectory);
    }

    private static final class FileVersion {
        final long size;
        final java.nio.file.attribute.FileTime modified;
        final java.nio.file.attribute.FileTime created;
        final Object fileKey;

        FileVersion(BasicFileAttributes attributes) {
            size = attributes.size();
            modified = attributes.lastModifiedTime();
            created = attributes.creationTime();
            fileKey = attributes.fileKey();
        }

        boolean same(FileVersion other) {
            // Windows may not expose fileKey. Creation time plus the process-local
            // write generation catches our atomic replacements in that case. An
            // external copy preserving every timestamp cannot be distinguished.
            return other != null && size == other.size && modified.equals(other.modified) &&
                    created.equals(other.created) && Objects.equals(fileKey, other.fileKey);
        }
    }

    private static final class PreparedChunk {
        final FileVersion version;
        final ChunkNavData data;

        PreparedChunk(FileVersion version, ChunkNavData data) {
            this.version = version;
            this.data = data;
        }
    }

    /** Decoded only once, then transferred to one matching world load. */
    public static final class PreparedChunks {
        private final String genus;
        private final Path directory;
        private final long generation;
        private Map<Path, PreparedChunk> chunks;

        private PreparedChunks(String genus, Path directory, long generation, Map<Path, PreparedChunk> chunks) {
            this.genus = genus;
            this.directory = directory;
            this.generation = generation;
            this.chunks = chunks;
        }

        private synchronized Map<Path, PreparedChunk> take(String genus, Path directory, MutationState state) {
            Map<Path, PreparedChunk> result = chunks;
            chunks = null;
            return result != null && Objects.equals(this.genus, genus) && this.directory.equals(directory) &&
                    generation == state.stableGeneration()
                    ? result : Collections.emptyMap();
        }
    }

    private static Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private FileVersion version(Path file) throws IOException {
        return new FileVersion(Files.readAttributes(file, BasicFileAttributes.class));
    }

    /** Reads and decodes without mutating files, the graph, or session state. */
    public PreparedChunks prepareChunks() {
        Map<Path, PreparedChunk> prepared = new HashMap<>();
        long generation = mutationState.stableGeneration();
        if(generation >= 0 && Files.exists(chunkDirectory)) {
            try(DirectoryStream<Path> stream = Files.newDirectoryStream(chunkDirectory, "*" + CHUNK_EXTENSION)) {
                for(Path file : stream) {
                    try {
                        FileVersion before = version(file);
                        ChunkNavData data = readChunkFile(file);
                        FileVersion after = version(file);
                        if(before.same(after)) prepared.put(normalized(file), new PreparedChunk(before, data));
                    } catch(IOException | RuntimeException ignored) {
                        // Normal load retains its existing error handling and corrupted-file cleanup.
                    }
                }
            } catch(IOException ignored) {
                // Normal load will retry directory enumeration.
            }
        }
        if(generation != mutationState.stableGeneration()) prepared.clear();
        return new PreparedChunks(genus, normalized(chunkDirectory), generation, prepared);
    }

    ChunkNavData readChunkFile(Path file) throws IOException {
        try(DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            return ChunkNavBinaryFormat.readChunk(in);
        }
    }

    private ChunkNavData cachedChunkIfCurrent(Path file, PreparedChunk candidate, long generation) {
        if(candidate == null) return null;
        synchronized(mutationState) {
            if(mutationState.stableGeneration() != generation) return null;
            try {
                return candidate.version.same(version(file)) ? candidate.data : null;
            } catch(IOException ignored) {
                return null;
            }
        }
    }

    /**
     * Get the directory where chunk files are stored.
     */
    public Path getChunkDirectory() {
        return chunkDirectory;
    }

    /**
     * Get the path to a specific chunk file.
     */
    public Path getChunkFile(long gridId) {
        return chunkDirectory.resolve(gridId + CHUNK_EXTENSION);
    }

    /**
     * Ensure the chunk directory exists.
     */
    public void ensureDirectoryExists() throws IOException {
        Files.createDirectories(chunkDirectory);
    }

    /**
     * Save a single chunk to its binary file.
     * Uses atomic write pattern (write to temp, then rename).
     */
    public void saveChunk(ChunkNavData chunk) throws IOException {
        mutationState.begin();
        try {
            ensureDirectoryExists();

            Path chunkFile = getChunkFile(chunk.gridId);
            Path tempFile = chunkFile.resolveSibling(chunk.gridId + ".tmp");

            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                ChunkNavBinaryFormat.writeChunk(chunk, out);
            }

            // Atomic rename
            try {
                Files.move(tempFile, chunkFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tempFile, chunkFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            mutationState.end();
        }
    }

    /**
     * Load a single chunk from its binary file.
     * Returns null if file doesn't exist or is corrupted.
     */
    public ChunkNavData loadChunk(long gridId) {
        Path chunkFile = getChunkFile(gridId);
        if (!Files.exists(chunkFile)) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(chunkFile)))) {
            return ChunkNavBinaryFormat.readChunk(in);
        } catch (IOException e) {
            System.err.println("ChunkNav: Failed to load chunk " + gridId + ": " + e.getMessage());
            // Delete corrupted file
            deleteCorruptedFile(chunkFile, gridId);
            return null;
        }
    }

    /**
     * Load all chunks from the directory.
     * Returns a list of successfully loaded chunks.
     * Corrupted files are deleted.
     */
    public List<ChunkNavData> loadAllChunks() {
        return loadAllChunks(null);
    }

    public List<ChunkNavData> loadAllChunks(PreparedChunks prepared) {
        List<ChunkNavData> chunks = new ArrayList<>();
        Map<Path, PreparedChunk> cached = prepared == null ? Collections.emptyMap()
                : prepared.take(genus, normalized(chunkDirectory), mutationState);
        long cachedGeneration = prepared == null ? -1 : prepared.generation;

        if (!Files.exists(chunkDirectory)) {
            return chunks;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(chunkDirectory, "*" + CHUNK_EXTENSION)) {
            for (Path file : stream) {
                try {
                    PreparedChunk candidate = cached.get(normalized(file));
                    ChunkNavData chunk = cachedChunkIfCurrent(file, candidate, cachedGeneration);
                    if(chunk == null) chunk = readChunkFile(file);
                    chunks.add(chunk);
                } catch (IOException e) {
                    String filename = file.getFileName().toString();
                    System.err.println("ChunkNav: Failed to load " + filename + ": " + e.getMessage());
                    // Extract gridId from filename and delete
                    try {
                        String gridIdStr = filename.replace(CHUNK_EXTENSION, "");
                        long gridId = Long.parseLong(gridIdStr);
                        deleteCorruptedFile(file, gridId);
                    } catch (NumberFormatException nfe) {
                        // Can't parse filename, just delete it
                        mutationState.begin();
                        try {
                            Files.delete(file);
                            System.out.println("ChunkNav: Deleted corrupted file: " + filename);
                        } catch (IOException deleteError) {
                            System.err.println("ChunkNav: Failed to delete corrupted file: " + filename);
                        } finally {
                            mutationState.end();
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("ChunkNav: Failed to list chunk directory: " + e.getMessage());
        }

        return chunks;
    }

    /**
     * Delete a chunk file.
     */
    public void deleteChunkFile(long gridId) {
        Path chunkFile = getChunkFile(gridId);
        mutationState.begin();
        try {
            Files.deleteIfExists(chunkFile);
        } catch (IOException e) {
            System.err.println("ChunkNav: Failed to delete chunk " + gridId + ": " + e.getMessage());
        } finally {
            mutationState.end();
        }
    }

    /**
     * Delete all chunk files from disk.
     * @return The number of files deleted
     */
    public int deleteAllChunkFiles() {
        if (!Files.exists(chunkDirectory)) {
            return 0;
        }

        mutationState.begin();
        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(chunkDirectory, "*" + CHUNK_EXTENSION)) {
            for (Path file : stream) {
                try {
                    Files.delete(file);
                    deleted++;
                } catch (IOException e) {
                    System.err.println("ChunkNav: Failed to delete file " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("ChunkNav: Failed to list chunk directory for deletion: " + e.getMessage());
        } finally {
            mutationState.end();
        }
        return deleted;
    }

    /**
     * Delete a corrupted file and log.
     */
    private void deleteCorruptedFile(Path file, long gridId) {
        mutationState.begin();
        try {
            Files.delete(file);
            System.out.println("ChunkNav: Deleted corrupted chunk file: " + gridId);
        } catch (IOException e) {
            System.err.println("ChunkNav: Failed to delete corrupted file " + gridId + ": " + e.getMessage());
        } finally {
            mutationState.end();
        }
    }

    /**
     * Get count of chunk files in directory.
     */
    public int getChunkCount() {
        if (!Files.exists(chunkDirectory)) {
            return 0;
        }

        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(chunkDirectory, "*" + CHUNK_EXTENSION)) {
            for (Path ignored : stream) {
                count++;
            }
        } catch (IOException e) {
            return 0;
        }
        return count;
    }

    /**
     * Check if the old JSON file exists (for migration).
     */
    public Path getOldJsonFilePath() {
        ProfileManager pm = new ProfileManager(genus);
        return pm.getConfigPath(ChunkNavConfig.STORAGE_FILENAME);
    }

    /**
     * Check if migration from JSON is needed.
     */
    public boolean needsMigration() {
        Path oldJson = getOldJsonFilePath();
        return Files.exists(oldJson) && getChunkCount() == 0;
    }

    /**
     * Delete the old JSON file after successful migration.
     */
    public void deleteOldJsonFile() {
        Path oldJson = getOldJsonFilePath();
        try {
            Files.deleteIfExists(oldJson);
            System.out.println("ChunkNav: Deleted old JSON file after migration");
        } catch (IOException e) {
            System.err.println("ChunkNav: Failed to delete old JSON file: " + e.getMessage());
        }
    }

    /**
     * Clean up any orphaned temp files.
     */
    public void cleanupTempFiles() {
        if (!Files.exists(chunkDirectory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(chunkDirectory, "*.tmp")) {
            for (Path file : stream) {
                mutationState.begin();
                try {
                    Files.delete(file);
                    System.out.println("ChunkNav: Cleaned up orphaned temp file: " + file.getFileName());
                } catch (IOException e) {
                    // Ignore
                } finally {
                    mutationState.end();
                }
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Check if the instance migration (neighbor wipe) needs to run.
     * This is a one-time migration when upgrading to V2 binary format with instanceId support.
     */
    public boolean needsInstanceMigration() {
        return !Files.exists(chunkDirectory.resolve(".instance_migrated"));
    }

    /**
     * Mark the instance migration as complete.
     */
    public void markInstanceMigrationDone() {
        try {
            Files.createDirectories(chunkDirectory);
            Files.createFile(chunkDirectory.resolve(".instance_migrated"));
        } catch (IOException e) {
            // Ignore - migration will re-run next time
        }
    }
}
