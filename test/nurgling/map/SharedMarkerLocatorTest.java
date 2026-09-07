package nurgling.map;

import haven.Coord;
import haven.MapFile;
import haven.ResCache;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SharedMarkerLocatorTest {
    @Test
    void exportsServerGridIdentityInsteadOfClientLocalSegmentIdentity() {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        MapFile.Segment segment = file.new Segment(77L);
        Coord segmentGrid = new Coord(-3, 4);
        long serverGridId = 9_876_543_210L;
        MapFile.PMarker marker = new MapFile.PMarker(
            file, segment.id, segmentGrid.mul(100).add(12, 34), "Shop", Color.CYAN, false);

        file.lock.writeLock().lock();
        try {
            segment.map.put(segmentGrid, serverGridId);
            file.segments.put(segment.id, segment);
        } finally {
            file.lock.writeLock().unlock();
        }

        SharedMarkerLocator.Ref ref = SharedMarkerLocator.export(file, marker);
        assertEquals(serverGridId, ref.gridId);
        assertEquals(new Coord(12, 34), ref.local);
    }

    @Test
    void markerOnUnknownGridCannotBeShared() {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        MapFile.Segment segment = file.new Segment(77L);
        MapFile.PMarker marker = new MapFile.PMarker(
            file, segment.id, new Coord(12, 34), "Shop", Color.CYAN, false);

        file.lock.writeLock().lock();
        try {
            file.segments.put(segment.id, segment);
        } finally {
            file.lock.writeLock().unlock();
        }

        assertNull(SharedMarkerLocator.export(file, marker));
    }
}
