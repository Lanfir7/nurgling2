package nurgling;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import nurgling.pf.CellsArray;
import nurgling.pf.Graph;
import nurgling.pf.NHitBoxD;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NGobPassageLifecycleTest {
    @Test
    void readyPassageRebuildsCellsAndDrawableChangeRetiresItsPosts() {
        Gob gob = NGob.getDummy(Coord2d.of(-110, -110), 0, (String) null);
        NHitBox whole = new NHitBox(Coord.of(-11, -3), Coord.of(11, 3), true);
        gob.ngob.hitBox = whole;
        NHitBox[] posts = {
                new NHitBox(Coord.of(-11, -3), Coord.of(-6, 3), true),
                new NHitBox(Coord.of(6, -3), Coord.of(11, 3), true)
        };

        gob.ngob.setPathfindingParts(posts);
        CellsArray separated = gob.ngob.getTrueCA();
        assertNotNull(separated);
        assertSame(whole, gob.ngob.hitBox);
        posts[0] = null; // the caller cannot alter a published array
        assertNotNull(gob.ngob.hitBoxParts()[0]);
        NHitBox[] snapshot = gob.ngob.hitBoxParts();
        snapshot[0].begin.x = 999;
        assertEquals(-11, gob.ngob.hitBoxParts()[0].begin.x);
        assertEquals(0, cellAt(separated, gob.rc));
        NHitBoxD walker = new NHitBoxD(gob.rc.sub(0.5, 0.5), gob.rc.add(0.5, 0.5));
        assertFalse(Graph.intersectsSolid(walker, gob));

        gob.ngob.setPathfindingParts(null); // next drawable/loading resource
        assertNull(gob.ngob.hitBoxParts());
        assertNotSame(separated, gob.ngob.getTrueCA());
        assertEquals(1, cellAt(gob.ngob.getTrueCA(), gob.rc));
        assertTrue(Graph.intersectsSolid(walker, gob));

        gob.ngob.setPathfindingParts(null); // unrelated drawable stays cached
        CellsArray unchanged = gob.ngob.getTrueCA();
        assertSame(unchanged, gob.ngob.getTrueCA());
        gob.ngob.setPathfindingParts(new NHitBox[]{
                new NHitBox(Coord.of(-11, -3), Coord.of(-6, 3), true),
                new NHitBox(Coord.of(6, -3), Coord.of(11, 3), true)
        }); // construction becoming a ready frame
        assertEquals(0, cellAt(gob.ngob.getTrueCA(), gob.rc));

        gob.ngob.beginPathfindingDrawable(); // new resource initially Loading
        assertNull(gob.ngob.hitBoxParts());
        gob.ngob.beginPathfindingDrawable(); // retry after resource becomes ready
        gob.ngob.hitBox = new NHitBox(Coord.of(36, 36), Coord.of(44, 44), true);
        gob.ngob.finishPathfindingParts(null);
        NHitBoxD fallback = NHitBoxD.solid(gob)[0];
        Coord2d fallbackCentre = fallback.getCircumscribedUL().add(fallback.getCircumscribedBR()).div(2);
        assertEquals(1, cellAt(gob.ngob.getTrueCA(), fallbackCentre));
        assertFalse(Graph.intersectsSolid(walker, gob));
    }

    private static int cellAt(CellsArray cells, Coord2d point) {
        Coord cell = nurgling.pf.Utils.toPfGrid(point).sub(cells.begin);
        return cells.cells[cell.x][cell.y];
    }
}
