package nurgling.map;

import haven.MapFile;
import haven.MiniMap;

public final class SharedMarkerImporter {
    private SharedMarkerImporter() {
    }

    public static MapFile.PMarker add(MapFile file, MiniMap.Location location,
                                      SharedMarkerCode.Marker shared) {
        file.lock.writeLock().lock();
        try {
            for (MapFile.Marker marker : file.markers) {
                if (marker instanceof MapFile.PMarker &&
                        marker.seg == location.seg.id &&
                        marker.tc.equals(location.tc) &&
                        marker.nm.equals(shared.name))
                    return (MapFile.PMarker)marker;
            }
            MapFile.PMarker marker = new MapFile.PMarker(file, location.seg.id,
                    location.tc, shared.name, shared.color, false);
            file.add(marker);
            return marker;
        } finally {
            file.lock.writeLock().unlock();
        }
    }
}
