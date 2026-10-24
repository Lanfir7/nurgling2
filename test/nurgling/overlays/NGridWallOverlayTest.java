package nurgling.overlays;

import haven.Coord;
import haven.MCache;
import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NGridWallOverlayTest {
    private static final Color EDGE = new Color(255, 100, 20, 180);
    private static final Color DIAGONAL = new Color(20, 140, 250, 95);
    private static final int VERTEX_FLOATS = 7;
    private static final int SEGMENT_FLOATS = 6 * VERTEX_FLOATS;
    private static final float GRID = 1100f;

    private static class RecordingMap extends MCache {
        final List<float[]> samples = new ArrayList<>();

        RecordingMap() { super(null); }

        @Override
        public float getcz(float x, float y) {
            samples.add(new float[]{x, y});
            return 17f;
        }
    }

    @Test
    void toggleCombinationsKeepBoundaryGeometryAndAddTwoDiagonalsPerGrid() {
        NGridWallOverlay overlay = new NGridWallOverlay();
        RecordingMap map = new RecordingMap();
        Set<Coord> grids = new HashSet<>(Arrays.asList(Coord.of(-1, 0), Coord.of(0, 0)));
        int edgeSegments = 7 * 30;
        int diagonalSegments = 4 * 42;

        assertEquals(0, overlay.buildVertices(map, grids, null, null).length);
        assertEquals(edgeSegments * SEGMENT_FLOATS, overlay.buildVertices(map, grids, EDGE, null).length);
        assertEquals(diagonalSegments * SEGMENT_FLOATS, overlay.buildVertices(map, grids, null, DIAGONAL).length);
        assertEquals((edgeSegments + diagonalSegments) * SEGMENT_FLOATS,
                overlay.buildVertices(map, grids, EDGE, DIAGONAL).length);
        assertEquals(0, overlay.buildVertices(map, Collections.emptySet(), EDGE, DIAGONAL).length);
    }

    @Test
    void diagonalCoordinatesColorsAndHeightStayInsideOwnerGrid() {
        NGridWallOverlay overlay = new NGridWallOverlay();
        RecordingMap map = new RecordingMap();
        Set<Coord> grids = Collections.singleton(Coord.of(-2, -3));
        float[] mesh = overlay.buildVertices(map, grids, EDGE, DIAGONAL);
        int firstDiagonal = 4 * 30 * SEGMENT_FLOATS;

        assertEquals(-2 * GRID, mesh[firstDiagonal]);
        assertEquals(3 * GRID, mesh[firstDiagonal + 1]);
        assertEquals(17.3f, mesh[firstDiagonal + 2], 0.001f);
        assertEquals(DIAGONAL.getRed() / 255f, mesh[firstDiagonal + 3], 0.0001f);
        assertEquals(DIAGONAL.getGreen() / 255f, mesh[firstDiagonal + 4], 0.0001f);
        assertEquals(DIAGONAL.getBlue() / 255f, mesh[firstDiagonal + 5], 0.0001f);
        assertEquals(DIAGONAL.getAlpha() / 255f, mesh[firstDiagonal + 6], 0.0001f);
        assertEquals(0f, mesh[firstDiagonal + 2 * VERTEX_FLOATS + 6]);
        assertEquals(EDGE.getAlpha() / 255f, mesh[6], 0.0001f);
        assertEquals(0f, mesh[2 * VERTEX_FLOATS + 6]);

        int secondDiagonal = firstDiagonal + 42 * SEGMENT_FLOATS;
        assertEquals(-GRID, mesh[secondDiagonal]);
        assertEquals(3 * GRID, mesh[secondDiagonal + 1]);
        for (float[] sample : map.samples) {
            assertTrue(sample[0] >= -2 * GRID && sample[0] < -GRID);
            assertTrue(sample[1] >= -3 * GRID && sample[1] < -2 * GRID);
        }
    }

    @Test
    void flatTerrainUsesVisualHeightAndEmptyRebuildClearsModel() throws Exception {
        Field current = NConfig.class.getDeclaredField("current");
        current.setAccessible(true);
        Object previous = current.get(null);
        try {
            current.set(null, new NConfig());
            RecordingMap map = new RecordingMap();
            NGridWallOverlay overlay = new NGridWallOverlay();
            Set<Coord> grids = Collections.singleton(Coord.of(0, 0));
            float[] normal = overlay.buildVertices(map, grids, null, DIAGONAL);
            assertEquals(17.3f, normal[2], 0.001f);

            NConfig.set(NConfig.Key.flatsurface, true);
            float[] flat = overlay.buildVertices(map, grids, null, DIAGONAL);
            assertEquals(0.3f, flat[2], 0.001f);
            overlay.rebuild(map, grids, null, DIAGONAL);
            Field model = NGridWallOverlay.class.getDeclaredField("model");
            model.setAccessible(true);
            assertNotNull(model.get(overlay));
            overlay.rebuild(map, Collections.emptySet(), null, DIAGONAL);
            assertNull(model.get(overlay));
        } finally {
            current.set(null, previous);
        }
    }
}
