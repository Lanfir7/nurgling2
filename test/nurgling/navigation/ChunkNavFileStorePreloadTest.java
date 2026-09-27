package nurgling.navigation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ChunkNavFileStorePreloadTest {
    @TempDir Path directory;

    private static class CountingStore extends ChunkNavFileStore {
        int diskReads;
        ChunkNavData replaceAfterNextRead;

        CountingStore(String genus, Path directory) {
            super(genus, directory);
        }

        @Override ChunkNavData readChunkFile(Path file) throws IOException {
            diskReads++;
            ChunkNavData result = super.readChunkFile(file);
            if(replaceAfterNextRead != null) {
                ChunkNavData replacement = replaceAfterNextRead;
                replaceAfterNextRead = null;
                saveChunk(replacement);
            }
            return result;
        }
    }

    private static final class BlockingStore extends CountingStore {
        boolean blockNextWrite;
        final CountDownLatch writeStarted = new CountDownLatch(1);
        final CountDownLatch allowWrite = new CountDownLatch(1);

        BlockingStore(String genus, Path directory) {
            super(genus, directory);
        }

        @Override public void ensureDirectoryExists() throws IOException {
            if(blockNextWrite) {
                blockNextWrite = false;
                writeStarted.countDown();
                try {
                    if(!allowWrite.await(5, TimeUnit.SECONDS)) throw new IOException("write test timed out");
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            super.ensureDirectoryExists();
        }
    }

    private static ChunkNavData chunk(long id, long updated) {
        ChunkNavData data = new ChunkNavData(id);
        data.lastUpdated = updated;
        return data;
    }

    private static Map<Long, ChunkNavData> byId(List<ChunkNavData> chunks) {
        return chunks.stream().collect(Collectors.toMap(data -> data.gridId, Function.identity()));
    }

    @Test
    void unchangedFileUsesPreparedDataOnceThenFallsBackToDisk() throws IOException {
        CountingStore store = new CountingStore("world", directory);
        store.saveChunk(chunk(1, 10));

        ChunkNavFileStore.PreparedChunks prepared = store.prepareChunks();
        assertEquals(1, store.diskReads);
        ChunkNavData first = store.loadAllChunks(prepared).get(0);
        assertEquals(1, store.diskReads);
        ChunkNavData second = store.loadAllChunks(prepared).get(0);
        assertEquals(2, store.diskReads);
        assertNotSame(first, second);
    }

    @Test
    void changedNewAndRemovedFilesUseCurrentDirectoryContents() throws IOException {
        CountingStore store = new CountingStore("world", directory);
        store.saveChunk(chunk(1, 10));
        store.saveChunk(chunk(2, 20));
        store.saveChunk(chunk(3, 30));
        ChunkNavFileStore.PreparedChunks prepared = store.prepareChunks();
        int afterPrepare = store.diskReads;

        store.saveChunk(chunk(1, 11));
        Path changed = store.getChunkFile(1);
        FileTime modified = Files.getLastModifiedTime(changed);
        Files.setLastModifiedTime(changed, FileTime.fromMillis(modified.toMillis() + 2_000));
        store.saveChunk(chunk(4, 40));
        Files.delete(store.getChunkFile(3));

        Map<Long, ChunkNavData> loaded = byId(store.loadAllChunks(prepared));
        assertEquals(3, loaded.size());
        assertEquals(11, loaded.get(1L).lastUpdated);
        assertEquals(20, loaded.get(2L).lastUpdated);
        assertEquals(40, loaded.get(4L).lastUpdated);
        assertFalse(loaded.containsKey(3L));
        // A process-local write invalidates the whole prepared snapshot.
        assertEquals(afterPrepare + 3, store.diskReads);
    }

    @Test
    void processLocalReplacementIsDetectedEvenWithSameSizeAndTimestamp() throws IOException {
        CountingStore store = new CountingStore("world", directory);
        store.saveChunk(chunk(1, 10));
        Path file = store.getChunkFile(1);
        BasicFileAttributes before = Files.readAttributes(file, BasicFileAttributes.class);
        ChunkNavFileStore.PreparedChunks prepared = store.prepareChunks();
        int afterPrepare = store.diskReads;

        store.saveChunk(chunk(1, 11));
        Files.setLastModifiedTime(file, before.lastModifiedTime());
        BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class);
        assertEquals(before.size(), after.size());
        assertEquals(before.lastModifiedTime(), after.lastModifiedTime());

        assertEquals(11, store.loadAllChunks(prepared).get(0).lastUpdated);
        assertEquals(afterPrepare + 1, store.diskReads);
    }

    @Test
    void externalUpdateWithChangedMetadataFallsBackToDisk() throws IOException {
        CountingStore store = new CountingStore("world", directory);
        store.saveChunk(chunk(1, 10));
        ChunkNavFileStore.PreparedChunks prepared = store.prepareChunks();
        int afterPrepare = store.diskReads;
        Path file = store.getChunkFile(1);
        FileTime oldModified = Files.getLastModifiedTime(file);

        try(DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            ChunkNavBinaryFormat.writeChunk(chunk(1, 11), out);
        }
        Files.setLastModifiedTime(file, FileTime.fromMillis(oldModified.toMillis() + 2_000));

        assertEquals(11, store.loadAllChunks(prepared).get(0).lastUpdated);
        assertEquals(afterPrepare + 1, store.diskReads);
    }

    @Test
    void activeProcessLocalWriteDisablesPreparedSnapshot() throws Exception {
        BlockingStore store = new BlockingStore("world", directory);
        store.saveChunk(chunk(1, 10));
        ChunkNavFileStore.PreparedChunks prepared = store.prepareChunks();
        int afterPrepare = store.diskReads;
        store.blockNextWrite = true;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try { store.saveChunk(chunk(1, 11)); }
            catch(Throwable error) { failure.set(error); }
        });
        writer.start();
        try {
            assertTrue(store.writeStarted.await(5, TimeUnit.SECONDS));
            assertEquals(10, store.loadAllChunks(prepared).get(0).lastUpdated);
            assertEquals(afterPrepare + 1, store.diskReads);
        } finally {
            store.allowWrite.countDown();
            writer.join(5_000);
        }
        assertFalse(writer.isAlive());
        assertNull(failure.get());
        assertEquals(11, store.loadAllChunks().get(0).lastUpdated);
    }

    @Test
    void mismatchedWorldDiscardsPreparedSnapshot() throws IOException {
        CountingStore source = new CountingStore("first", directory);
        source.saveChunk(chunk(1, 10));
        ChunkNavFileStore.PreparedChunks prepared = source.prepareChunks();
        CountingStore otherWorld = new CountingStore("second", directory);

        assertEquals(10, otherWorld.loadAllChunks(prepared).get(0).lastUpdated);
        assertEquals(1, otherWorld.diskReads);
        source.loadAllChunks(prepared);
        assertEquals(2, source.diskReads);
    }

    @Test
    void changeDuringPrefetchFallsBackToLatestFileWithoutDeletingIt() throws IOException {
        CountingStore store = new CountingStore("world", directory);
        store.saveChunk(chunk(1, 10));
        store.replaceAfterNextRead = chunk(1, 11);

        ChunkNavFileStore.PreparedChunks prepared = store.prepareChunks();
        assertTrue(Files.exists(store.getChunkFile(1)));
        assertEquals(11, store.loadAllChunks(prepared).get(0).lastUpdated);
        assertEquals(2, store.diskReads);
    }

    @Test
    void corruptPrefetchLeavesOriginalCleanupForNormalLoad() throws IOException {
        CountingStore store = new CountingStore("world", directory);
        Files.createDirectories(directory);
        Path corrupt = store.getChunkFile(1);
        Files.write(corrupt, new byte[]{1, 2, 3});

        ChunkNavFileStore.PreparedChunks prepared = store.prepareChunks();
        assertTrue(Files.exists(corrupt));
        assertTrue(store.loadAllChunks(prepared).isEmpty());
        assertFalse(Files.exists(corrupt));
    }
}
