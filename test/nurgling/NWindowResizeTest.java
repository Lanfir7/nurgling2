package nurgling;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NWindowResizeTest {
    @Test
    void detectsEveryEdgeAndCornerInsideResizeBorder() {
        Coord size = Coord.of(300, 200);

        assertEquals(NWindowResize.Edge.TOP_LEFT, NWindowResize.hit(Coord.of(2, 2), size, 5));
        assertEquals(NWindowResize.Edge.TOP, NWindowResize.hit(Coord.of(150, 2), size, 5));
        assertEquals(NWindowResize.Edge.TOP_RIGHT, NWindowResize.hit(Coord.of(297, 2), size, 5));
        assertEquals(NWindowResize.Edge.LEFT, NWindowResize.hit(Coord.of(2, 100), size, 5));
        assertEquals(NWindowResize.Edge.RIGHT, NWindowResize.hit(Coord.of(297, 100), size, 5));
        assertEquals(NWindowResize.Edge.BOTTOM_LEFT, NWindowResize.hit(Coord.of(2, 197), size, 5));
        assertEquals(NWindowResize.Edge.BOTTOM, NWindowResize.hit(Coord.of(150, 197), size, 5));
        assertEquals(NWindowResize.Edge.BOTTOM_RIGHT, NWindowResize.hit(Coord.of(297, 197), size, 5));
        assertEquals(NWindowResize.Edge.NONE, NWindowResize.hit(Coord.of(150, 100), size, 5));
        assertEquals(NWindowResize.Edge.NONE, NWindowResize.hit(Coord.of(-1, 100), size, 5));
    }

    @Test
    void rightAndBottomEdgesGrowWithoutMovingWindow() {
        NWindowResize.Result result = NWindowResize.drag(
                NWindowResize.Edge.BOTTOM_RIGHT,
                Coord.of(40, 30), Coord.of(300, 200), Coord.of(25, 15), Coord.of(160, 120));

        assertEquals(Coord.of(40, 30), result.position);
        assertEquals(Coord.of(325, 215), result.size);
    }

    @Test
    void leftAndTopEdgesMoveWindowAndKeepOppositeEdgesFixed() {
        NWindowResize.Result result = NWindowResize.drag(
                NWindowResize.Edge.TOP_LEFT,
                Coord.of(40, 30), Coord.of(300, 200), Coord.of(25, 15), Coord.of(160, 120));

        assertEquals(Coord.of(65, 45), result.position);
        assertEquals(Coord.of(275, 185), result.size);
        assertEquals(340, result.position.x + result.size.x);
        assertEquals(230, result.position.y + result.size.y);
    }

    @Test
    void minimumSizeClampsLeftAndTopWithoutMovingOppositeEdges() {
        NWindowResize.Result result = NWindowResize.drag(
                NWindowResize.Edge.TOP_LEFT,
                Coord.of(40, 30), Coord.of(300, 200), Coord.of(500, 500), Coord.of(240, 150));

        assertEquals(Coord.of(100, 80), result.position);
        assertEquals(Coord.of(240, 150), result.size);
        assertEquals(340, result.position.x + result.size.x);
        assertEquals(230, result.position.y + result.size.y);
    }
}
