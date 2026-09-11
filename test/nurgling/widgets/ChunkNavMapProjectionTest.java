package nurgling.widgets;

import haven.Coord;
import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkNavMapProjectionTest {
    /*
     * Catches a regression where centering leaves the selected texture point away
     * from the canvas center (for example, by resetting pan to zero).
     */
    @Test
    void centeringPanPlacesTexturePointAtCanvasCenterAtCurrentZoom() {
        float[] pan = ChunkNavMapProjection.centeredPan(new Coord(1000, 600), new Coord(100, 100),
                5.0f, new Coord2d(25, 75));
        Coord2d projected = ChunkNavMapProjection.project(new Coord(1000, 600), new Coord(100, 100),
                5.0f, pan[0], pan[1], new Coord2d(25, 75));

        assertEquals(500.0, projected.x, 0.000001);
        assertEquals(300.0, projected.y, 0.000001);
    }

    /*
     * Catches a marker regression that anchors at the chunk center instead of the
     * player's exact local tile location.
     */
    @Test
    void playerTexturePointIncludesChunkOffsetAndExactLocalTile() {
        Coord2d point = ChunkNavMapProjection.texturePoint(new Coord(2, 3), new Coord(4, 5),
                new Coord2d(50, 25), 50);

        assertEquals(125.0, point.x, 0.000001);
        assertEquals(112.5, point.y, 0.000001);
    }

    /* Catches restoring the old 10x zoom cap or allowing unbounded zoom. */
    @Test
    void wheelZoomGrowsPastTenAndStopsAtMaximum() {
        assertEquals(12.0f, ChunkNavMapProjection.zoomAfterWheel(10.0f, -1), 0.0001f);
        assertEquals(50.0f, ChunkNavMapProjection.zoomAfterWheel(49.0f, -1), 0.0001f);
    }

    /* Catches snapping the marker to a whole tile before projecting it. */
    @Test
    void playerTexturePointPreservesFractionalLocalTileCoordinates() {
        Coord2d point = ChunkNavMapProjection.texturePoint(new Coord(2, 3), new Coord(4, 5),
                new Coord2d(50.5, 25.75), 50);

        assertEquals(125.25, point.x, 0.000001);
        assertEquals(112.875, point.y, 0.000001);
    }
}
