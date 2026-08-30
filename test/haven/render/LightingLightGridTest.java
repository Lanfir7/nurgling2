package haven.render;

import haven.GSettings;
import haven.MapView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightingLightGridTest {
    private static final Projection PROJECTION = Projection.ortho(-100, 100, -100, 100, 1, 1000);

    private static Object[][] pointLight() {
        return new Object[][] {{
                new float[] {0, 0, 0, 1},
                new float[] {1, 1, 1, 1},
                new float[] {1, 1, 1, 1},
                new float[] {0, 0, -10, 1},
                1.0f, 0.0f, 0.01f, 0.05f
        }};
    }

    @Test
    void unchangedLightingReusesCompiledGrid() {
        Lighting.LightGrid grid = new Lighting.LightGrid(4, 4, 4);
        Object[][] lights = pointLight();

        State first = grid.compile(lights, PROJECTION);
        State second = grid.compile(pointLight(), PROJECTION);

        assertSame(first, second);
    }

    @Test
    void mutatingSharedLightDataInvalidatesCompiledGrid() {
        Lighting.LightGrid grid = new Lighting.LightGrid(4, 4, 4);
        Object[][] lights = pointLight();
        State first = grid.compile(lights, PROJECTION);

        ((float[]) lights[0][0])[0] = 0.5f;
        State second = grid.compile(lights, PROJECTION);

        assertNotSame(first, second);
    }

    @Test
    void defaultZonedLightingKeepsGridUploadBelow128KiB() {
        MapView.LightCompiler compiler = new MapView.LightCompiler(GSettings.defaults());
        Lighting.LightGrid.GridLights compiled = assertInstanceOf(
                Lighting.LightGrid.GridLights.class,
                compiler.compile(new Object[0][], PROJECTION));

        assertTrue(compiled.lstex.tex.image(0).size() <= 128 * 1024);
    }
}
