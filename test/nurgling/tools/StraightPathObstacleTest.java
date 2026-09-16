package nurgling.tools;

import haven.Coord;
import haven.Coord2d;
import haven.Loading;
import haven.MCache;
import haven.MapMesh;
import haven.Tiler;
import haven.resutil.Ridges;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightPathObstacleTest {
    private static final Coord2d A = tileCenter(0, 0);
    private static final Coord2d B = tileCenter(10, 0);

    private static Coord2d tileCenter(int tx, int ty) {
        return Coord2d.of(tx, ty).mul(MCache.tilesz).add(MCache.tilehsz);
    }

    @Test
    void blocksBrokenRidge() {
        assertTrue(StraightPathObstacle.blocked(new BrokenRidgeMap(), A, B));
    }

    @Test
    void blocksCaveWall() {
        assertTrue(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/cave"), A, B));
    }

    @Test
    void blocksRockWall() {
        assertTrue(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/rocks"), A, B));
    }

    @Test
    void grassIsClear() {
        assertFalse(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/grass"), A, B));
    }

    @Test
    void deepCaveFloorIsClear() {
        assertFalse(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/deepcave"), A, B));
    }

    @Test
    void deepTangleFloorIsClear() {
        assertFalse(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/deeptangle"), A, B));
    }

    @Test
    void caveMouthTileIsClear() {
        assertFalse(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/ridges/cavein"), A, B));
        assertFalse(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/ridges/caveout"), A, B));
    }

    @Test
    void loadingIsNotAHitAndDoesNotThrow() {
        assertFalse(StraightPathObstacle.blocked(new LoadingMap(), A, B));
    }

    @Test
    void obstaclePastSampleCapIsIgnored() {
        Coord2d far = tileCenter(400, 0);
        assertFalse(StraightPathObstacle.blocked(new SingleWallMap(301, 0, "gfx/tiles/cave"), A, far));
        assertTrue(StraightPathObstacle.blocked(new SingleWallMap(20, 0, "gfx/tiles/cave"), A, far));
    }

    @Test
    void samePointChecksThatTile() {
        assertTrue(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/cave"), A, A));
        assertFalse(StraightPathObstacle.blocked(new NamedMap("gfx/tiles/grass"), A, A));
    }

    @Test
    void cacheRetriesIncompleteAfter200ms() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(0);
        StraightPathObstacle.Cache cache = new StraightPathObstacle.Cache(now::get);
        Coord2d a = tileCenter(0, 0);
        Coord2d b = tileCenter(10, 0);
        assertFalse(cache.blocked(new LoadingMap(), a, b));
        now.set(100_000_000L);
        assertFalse(cache.blocked(new NamedMap("gfx/tiles/cave"), a, b));
        now.set(200_000_000L);
        assertTrue(cache.blocked(new NamedMap("gfx/tiles/cave"), a, b));
    }

    @Test
    void cacheKeepsCompleteMiss() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(0);
        StraightPathObstacle.Cache cache = new StraightPathObstacle.Cache(now::get);
        Coord2d a = tileCenter(0, 0);
        Coord2d b = tileCenter(10, 0);
        assertFalse(cache.blocked(new NamedMap("gfx/tiles/grass"), a, b));
        now.set(1_000_000_000L);
        assertFalse(cache.blocked(new NamedMap("gfx/tiles/cave"), a, b));
    }

    private static class NamedMap extends MCache {
        private final String name;
        private final Tiler tiler = new GrassTiler();

        NamedMap(String name) {
            super(null);
            this.name = name;
        }

        @Override
        public int gettile(Coord coord) {
            return 0;
        }

        @Override
        public String tilesetname(int tile) {
            return name;
        }

        @Override
        public Tiler tiler(int tile) {
            return tiler;
        }

        @Override
        public double getfz(Coord coord) {
            return 0;
        }
    }

    private static class SingleWallMap extends MCache {
        private final int wx, wy;
        private final String wallName;
        private final Tiler grass = new GrassTiler();

        SingleWallMap(int wx, int wy, String wallName) {
            super(null);
            this.wx = wx;
            this.wy = wy;
            this.wallName = wallName;
        }

        @Override
        public int gettile(Coord coord) {
            return (coord.x == wx && coord.y == wy) ? 1 : 0;
        }

        @Override
        public String tilesetname(int tile) {
            return (tile == 1) ? wallName : "gfx/tiles/grass";
        }

        @Override
        public Tiler tiler(int tile) {
            return grass;
        }

        @Override
        public double getfz(Coord coord) {
            return 0;
        }
    }

    private static class LoadingMap extends MCache {
        LoadingMap() {
            super(null);
        }

        @Override
        public int gettile(Coord coord) {
            throw new Loading("unloaded");
        }

        @Override
        public String tilesetname(int tile) {
            return "gfx/tiles/grass";
        }

        @Override
        public Tiler tiler(int tile) {
            throw new Loading("unloaded");
        }

        @Override
        public double getfz(Coord coord) {
            throw new Loading("unloaded");
        }
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

    private static class GrassTiler extends Tiler {
        GrassTiler() {
            super(0);
        }

        @Override
        public void lay(MapMesh map, Random random, Coord local, Coord global) {
        }

        @Override
        public void trans(MapMesh map, Random random, Tiler ground, Coord local, Coord global,
                          int z, int borderMask, int cornerMask) {
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
