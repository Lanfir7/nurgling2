package haven;

import haven.render.Camera;
import haven.render.Projection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapViewCompassProjectionTest {
    private static final Area VIEW = Area.sized(new Coord(200, 100));

    private static HomoCoord4f clip(Camera camera, Projection projection, Coord3f world) {
        Coord3f eye = camera.fin(Matrix4f.id).mul4(world);
        float[] clip = projection.toclip(eye);
        return new HomoCoord4f(clip[0], clip[1], clip[2], clip[3]);
    }

    @Test
    void perspectiveTargetInFrontKeepsItsProjectedDirection() {
        Coord3f screen = CompassProjection.toview(new HomoCoord4f(0.5f, 0.25f, 0, 1), VIEW);

        assertEquals(150.0f, screen.x, 0.001f);
        assertEquals(37.5f, screen.y, 0.001f);
    }

    @Test
    void perspectiveTargetBehindCameraDoesNotMirrorToTheOppositeDirection() {
        Coord3f screen = CompassProjection.toview(new HomoCoord4f(-0.5f, -0.25f, 0, -1), VIEW);

        assertEquals(50.0f, screen.x, 0.001f);
        assertEquals(62.5f, screen.y, 0.001f);
    }

    @Test
    void orthographicTargetKeepsItsProjectedDirection() {
        Coord3f screen = CompassProjection.toview(new HomoCoord4f(-0.5f, 0.25f, 0, 1), VIEW);

        assertEquals(50.0f, screen.x, 0.001f);
        assertEquals(37.5f, screen.y, 0.001f);
    }

    @Test
    void targetOnCameraPlaneStillProducesFiniteDirectionCoordinates() {
        Coord3f screen = CompassProjection.toview(new HomoCoord4f(1, -1, 0, 0), VIEW);

        assertTrue(Float.isFinite(screen.x));
        assertTrue(Float.isFinite(screen.y));
    }

    @Test
    void badCameraShowsTargetBehindCameraAtTheBackEdge() {
        Camera camera = Camera.pointed(new Coord3f(0, 0, 15), 50,
                (float)Math.PI / 4, 0);
        Projection projection = Projection.frustum(-0.5f, 0.5f, -0.25f, 0.25f, 1, 5000);

        Coord3f screen = CompassProjection.toview(
                clip(camera, projection, new Coord3f(100, 0, 0)), VIEW);

        assertTrue(screen.y > VIEW.sz().y / 2.0f);
    }

    @Test
    void rotatingBadCameraHalfTurnMovesTheSameTargetToTheFrontEdge() {
        Camera camera = Camera.pointed(new Coord3f(0, 0, 15), 50,
                (float)Math.PI / 4, (float)Math.PI);
        Projection projection = Projection.frustum(-0.5f, 0.5f, -0.25f, 0.25f, 1, 5000);

        Coord3f screen = CompassProjection.toview(
                clip(camera, projection, new Coord3f(100, 0, 0)), VIEW);

        assertTrue(screen.y < VIEW.sz().y / 2.0f);
    }

    @Test
    void orthographicCameraKeepsTargetOnItsProjectedSide() {
        Matrix4f view = Camera.makepointed(new Matrix4f(), new Coord3f(0, 0, 15), 500,
                (float)Math.PI / 6, -(float)Math.PI / 4);
        Projection projection = Projection.ortho(-141.42136f, 141.42136f,
                -70.71068f, 70.71068f, 1, 5000);
        float[] clip = projection.toclip(view.mul4(new Coord3f(100, 0, 0)));

        Coord3f screen = CompassProjection.toview(
                new HomoCoord4f(clip[0], clip[1], clip[2], clip[3]), VIEW);

        assertTrue(screen.x > VIEW.sz().x / 2.0f);
        assertTrue(screen.y > VIEW.sz().y / 2.0f);
    }
}
