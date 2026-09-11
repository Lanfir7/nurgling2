package nurgling.navigation;

import haven.Coord;
import haven.MCache;
import haven.MapMesh;
import haven.Tiler;
import haven.resutil.Ridges;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkNavRecorderRidgeTest {
    @Test
    void blocksBrokenRidgeUsingCanonicalRidgeGeometry() throws Exception {
        ChunkNavRecorder recorder = new ChunkNavRecorder(new ChunkNavGraph());
        Method isTileBlocked = ChunkNavRecorder.class.getDeclaredMethod(
                "isTileBlocked", MCache.class, Coord.class);
        isTileBlocked.setAccessible(true);

        boolean blocked = (boolean) isTileBlocked.invoke(
                recorder, new BrokenRidgeMap(), Coord.of(10, 10));

        assertTrue(blocked);
    }

    @Test
    void blocksTileWhenRidgeGeometryLookupFails() throws Exception {
        ChunkNavRecorder recorder = new ChunkNavRecorder(new ChunkNavGraph());
        Method isTileBlocked = ChunkNavRecorder.class.getDeclaredMethod(
                "isTileBlocked", MCache.class, Coord.class);
        isTileBlocked.setAccessible(true);

        boolean blocked = (boolean) isTileBlocked.invoke(
                recorder, new RidgeLookupFailureMap(), Coord.of(10, 10));

        assertTrue(blocked);
    }

    private static class BrokenRidgeMap extends MCache {
        private final Tiler ridge = new RidgeTiler();

        BrokenRidgeMap() {
            super(null);
        }

        @Override
        public int gettile(Coord coord) {
            return 0;
        }

        @Override
        public String tilesetname(int tile) {
            return "gfx/tiles/grass";
        }

        @Override
        public Tiler tiler(int tile) {
            return ridge;
        }

        @Override
        public double getfz(Coord coord) {
            return (coord.x & 1) == 0 ? 0 : 5;
        }
    }

    private static class RidgeLookupFailureMap extends MCache {
        RidgeLookupFailureMap() {
            super(null);
        }

        @Override
        public int gettile(Coord coord) {
            return 0;
        }

        @Override
        public Tiler tiler(int tile) {
            throw new RuntimeException("ridge data unavailable");
        }
    }

    private static class RidgeTiler extends Tiler implements Ridges.RidgeTile {
        RidgeTiler() {
            super(0);
        }

        @Override
        public double breakz() {
            return 1;
        }

        @Override
        public void lay(MapMesh map, Random random, Coord local, Coord global) {
        }

        @Override
        public void trans(MapMesh map, Random random, Tiler ground, Coord local, Coord global,
                          int z, int borderMask, int cornerMask) {
        }
    }
}
