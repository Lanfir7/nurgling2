package nurgling.map;

import haven.Coord;
import haven.MapFile;
import haven.MiniMap;
import haven.ResCache;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SharedMarkerImporterTest {
    @Test
    void addsOnePermanentMarkerAndReusesItWhenCodeIsImportedAgain() {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        MapFile.Segment segment = file.new Segment(77L);
        MiniMap.Location location = new MiniMap.Location(segment, new Coord(412, 734));
        SharedMarkerCode.Marker shared = SharedMarkerCode.decode(SharedMarkerCode.encode(
            "Northern shop", "world-16", 42L, new Coord(12, 34), new Color(10, 20, 30)));

        MapFile.PMarker first = SharedMarkerImporter.add(file, location, shared);
        MapFile.PMarker second = SharedMarkerImporter.add(file, location, shared);

        assertSame(first, second);
        assertEquals(1, file.markers.size());
        assertEquals(segment.id, first.seg);
        assertEquals(location.tc, first.tc);
        assertEquals("Northern shop", first.nm);
        assertEquals(new Color(10, 20, 30).getRGB(), first.color.getRGB());
    }
}
