package nurgling.pf;

import haven.Coord2d;
import nurgling.NHitBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TunnelPassageGeometryTest {
    private static NHitBox[] posts() {
        return new NHitBox[]{
                new NHitBox(Coord2d.of(-11, -3).floor(), Coord2d.of(-6, 3).ceil(), true),
                new NHitBox(Coord2d.of(6, -3).floor(), Coord2d.of(11, 3).ceil(), true)
        };
    }

    private static boolean hits(NHitBoxD[] solids, Coord2d point) {
        NHitBoxD probe = new NHitBoxD(point.sub(0.5, 0.5), point.add(0.5, 0.5));
        for (NHitBoxD solid : solids)
            if (probe.intersects(solid, true)) return true;
        return false;
    }

    @Test
    void rotatedPostsBlockThemselvesButLeaveTheMiddleOpen() {
        Coord2d centre = Coord2d.of(-110, -110);
        for (double angle : new double[]{0, Math.PI / 2, Math.PI, Math.PI / 4}) {
            NHitBoxD[] solids = NHitBoxD.parts(posts(), centre, angle);
            assertEquals(2, solids.length);
            assertTrue(hits(solids, centre.add(Coord2d.of(-8, 0).rot(angle))), "left post at " + angle);
            assertTrue(hits(solids, centre.add(Coord2d.of(8, 0).rot(angle))), "right post at " + angle);
            assertFalse(hits(solids, centre), "opening at " + angle);
        }
    }

    @Test
    void pathfindingCellsMarkEachPostWithoutFillingTheGap() {
        Coord2d centre = Coord2d.of(-110, -110);
        CellsArray grid = new CellsArray(NHitBoxD.parts(posts(), centre, 0));
        assertEquals(0, cellAt(grid, centre));
        assertEquals(1, cellAt(grid, centre.add(-8, 0)));
        assertEquals(1, cellAt(grid, centre.add(8, 0)));

        CellsArray fallback = new CellsArray(
                new NHitBox(Coord2d.of(-11, -3).floor(), Coord2d.of(11, 3).ceil(), true), 0, centre);
        assertEquals(1, cellAt(fallback, centre));
        assertNull(NHitBoxD.parts(new NHitBox[0], centre, 0));
    }

    private static int cellAt(CellsArray cells, Coord2d point) {
        haven.Coord cell = Utils.toPfGrid(point).sub(cells.begin);
        return cells.cells[cell.x][cell.y];
    }
}
