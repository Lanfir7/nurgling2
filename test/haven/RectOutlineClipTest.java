package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RectOutlineClipTest {
    @Test
    void fullyBelowVisibleStripDropsTheWholeFrame() {
        Coord textEntry = Coord.of(0, 8);
        Coord br = textEntry.add(40, 20).sub(1, 1);
        assertFalse(RectOutlineClip.anyEdgeVisible(textEntry, br, Coord.z, Coord.of(210, 4)));
    }

    @Test
    void fullyInsideListKeepsTheFrame() {
        Coord ul = Coord.of(0, 8);
        Coord br = ul.add(40, 20).sub(1, 1);
        assertTrue(RectOutlineClip.anyEdgeVisible(ul, br, Coord.z, Coord.of(210, 130)));
    }

    @Test
    void straddlingBottomOfListKeepsVisibleEdges() {
        Coord ul = Coord.of(0, 120);
        Coord br = ul.add(40, 20).sub(1, 1);
        assertTrue(RectOutlineClip.anyEdgeVisible(ul, br, Coord.z, Coord.of(210, 130)));
    }

    @Test
    void fullyAboveListDropsTheFrame() {
        Coord ul = Coord.of(0, -24);
        Coord br = ul.add(40, 20).sub(1, 1);
        assertFalse(RectOutlineClip.anyEdgeVisible(ul, br, Coord.z, Coord.of(210, 130)));
    }

    @Test
    void horizontalLineFullyBelowClipIsRejected() {
        assertTrue(RectOutlineClip.aabbMiss(Coord.of(0, 8), Coord.of(39, 8),
                Coord.z, Coord.of(210, 4)));
    }
}
