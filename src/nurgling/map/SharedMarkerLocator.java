package nurgling.map;

import haven.Coord;
import haven.MapFile;

import static haven.MCache.cmaps;

/** Converts a local map-file marker into the server-stable grid reference shared between clients. */
public final class SharedMarkerLocator {
    private SharedMarkerLocator() {
    }

    public static final class Ref {
        public final long gridId;
        public final Coord local;

        private Ref(long gridId, Coord local) {
            this.gridId = gridId;
            this.local = local;
        }
    }

    public static Ref export(MapFile file, MapFile.Marker marker) {
        if (file == null || marker == null)
            return null;
        file.lock.readLock().lock();
        try {
            MapFile.Segment segment = file.segments.get(marker.seg);
            if (segment == null)
                return null;
            Coord segmentGrid = marker.tc.div(cmaps);
            Long gridId = segment.map.get(segmentGrid);
            if (gridId == null)
                return null;
            return new Ref(gridId, marker.tc.sub(segmentGrid.mul(cmaps)));
        } finally {
            file.lock.readLock().unlock();
        }
    }
}
