package nurgling;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NHitBoxPassagePartsTest {
    private static Coord2d[] rect(double left, double top, double right, double bottom) {
        return new Coord2d[]{Coord2d.of(left, top), Coord2d.of(right, top),
                Coord2d.of(right, bottom), Coord2d.of(left, bottom)};
    }

    @Test
    void onlyTheThreeReadyFrameObstaclesExposeSeparatePosts() {
        Coord2d[][] posts = {rect(-11, -3, -6, 3), rect(6, -3, 11, 3)};
        for (String frame : new String[]{"timbertunnel", "reinforcedtunnel", "stonearchtunnel"}) {
            NHitBox[] parts = NHitBox.passageParts("gfx/terobjs/" + frame, "", posts);
            assertNotNull(parts, frame);
            assertEquals(2, parts.length);
            assertEquals(-11, parts[0].begin.x);
            assertEquals(11, parts[1].end.x);
        }
        assertNull(NHitBox.passageParts("gfx/terobjs/other", "", posts));
        assertNull(NHitBox.passageParts("gfx/terobjs/timbertunnel", "build", posts));
        assertNull(NHitBox.passageParts("gfx/terobjs/timbertunnel", "", new Coord2d[0][]));
        assertNull(NHitBox.passageParts("gfx/terobjs/timbertunnel", "",
                new Coord2d[][]{rect(-11, -3, -6, 3), {Coord2d.of(1, 1)}}));
        assertNull(NHitBox.passageParts("gfx/terobjs/timbertunnel", "",
                new Coord2d[][]{rect(Double.NaN, -3, -6, 3)}));
    }
}
