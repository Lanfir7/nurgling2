package haven.resutil;

import haven.Coord3f;
import haven.Glob;
import haven.MCache;
import haven.Tiler;
import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterTileFlatWorldTest {
    @Test
    void drawStateUsesRenderedSurfaceHeightForWaterFog() throws Exception {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            Glob glob = new Glob(null);
            replaceMap(glob, new FixedHeightMap(42.5f));
            WaterTile tile = new WaterTile(1, "gfx/tiles/water", Tiler.MCons.nil, 5);

            NConfig.set(NConfig.Key.flatsurface, true);
            WaterTile.ObFog flatFog = (WaterTile.ObFog) tile.drawstate(glob, Coord3f.o);
            assertEquals(0f, flatFog.basez);

            NConfig.set(NConfig.Key.flatsurface, false);
            WaterTile.ObFog reliefFog = (WaterTile.ObFog) tile.drawstate(glob, Coord3f.o);
            assertEquals(42.5f, reliefFog.basez);
        } finally {
            NConfig.current = previous;
        }
    }

    private static void replaceMap(Glob glob, MCache map) throws Exception {
        Field field = Glob.class.getField("map");
        field.setAccessible(true);
        field.set(glob, map);
    }

    private static class FixedHeightMap extends MCache {
        private final float height;

        FixedHeightMap(float height) {
            super(null);
            this.height = height;
        }

        @Override
        public float getcz(float px, float py) {
            return height;
        }
    }
}
