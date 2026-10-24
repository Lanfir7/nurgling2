package nurgling.navigation;

import haven.Coord;
import haven.Coord2d;
import haven.MCache;
import nurgling.NHitBox;
import nurgling.pf.NHitBoxD;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChunkNavRecorderPassageTest {
    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xffffffffL); }

    @Test
    void savedRouteMarksOnlyPostCentresAtNegativeWorldCoordinates() {
        NHitBox[] posts = {
                new NHitBox(Coord.of(-11, -3), Coord.of(-6, 3), true),
                new NHitBox(Coord.of(6, -3), Coord.of(11, 3), true)
        };
        NHitBoxD[] solids = NHitBoxD.parts(posts, Coord2d.of(-110, -110), 0);
        Set<Long> blocked = ChunkNavRecorder.partBlockedCells(solids, Coord.of(-30, -30));
        assertTrue(blocked.contains(key(8, 10)));
        assertTrue(blocked.contains(key(11, 10)));
        assertFalse(blocked.contains(key(10, 10)));
    }

    @Test
    void diagonalPartDoesNotBlockEveryCellInItsCircumscribedBox() {
        NHitBoxD[] solids = NHitBoxD.parts(new NHitBox[]{
                new NHitBox(Coord.of(-22, -1), Coord.of(-6, 1), true)
        }, Coord2d.of(110, 110), Math.PI / 4);
        Set<Long> blocked = ChunkNavRecorder.partBlockedCells(solids, Coord.of(0, 0));
        assertFalse(blocked.isEmpty());
        Coord2d ul = solids[0].getCircumscribedUL();
        Coord2d br = solids[0].getCircumscribedBR();
        boolean clearInsideBounds = false;
        for (int x = (int) Math.floor(ul.x / MCache.tilehsz.x);
             x <= (int) Math.floor(br.x / MCache.tilehsz.x); x++) {
            for (int y = (int) Math.floor(ul.y / MCache.tilehsz.y);
                 y <= (int) Math.floor(br.y / MCache.tilehsz.y); y++) {
                Coord2d centre = Coord2d.of((x + 0.5) * MCache.tilehsz.x,
                                            (y + 0.5) * MCache.tilehsz.y);
                if (!solids[0].contains(centre, true) && !blocked.contains(key(x, y)))
                    clearInsideBounds = true;
            }
        }
        assertTrue(clearInsideBounds, "rotation must not fill its bounding square");
    }
}
