package nurgling.navigation;

import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ChunkNavDataConcurrencyTest {

    @Test
    void routingCollectionsAllowRecorderUpdatesDuringIteration() {
        ChunkNavData data = new ChunkNavData();
        data.connectedChunks.add(1L);
        data.connectedChunks.add(2L);
        data.portals.add(new ChunkPortal());
        data.portals.add(new ChunkPortal());

        assertDoesNotThrow(() -> {
            Iterator<Long> chunks = data.connectedChunks.iterator();
            chunks.next();
            data.connectedChunks.add(3L);
            while (chunks.hasNext())
                chunks.next();

            Iterator<ChunkPortal> portals = data.portals.iterator();
            portals.next();
            data.portals.add(new ChunkPortal());
            while (portals.hasNext())
                portals.next();
        });
    }
}
