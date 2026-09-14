package haven.resutil;

import haven.Coord;
import haven.MCache;
import haven.MapMesh;
import haven.Surface.Vertex;
import haven.Tiler;
import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RidgesFlatWorldTest {
    @Test
    void flatRidgeHeightKeepsCliffsVisibleWithoutRecreatingTheirFullRelief() {
        assertEquals(4f, Ridges.flatRidgeHeight(0f));
        assertEquals(4f, Ridges.flatRidgeHeight(20f), 0.0001f);
        assertEquals(5f, Ridges.flatRidgeHeight(100f));
    }

    @Test
    void flatEndsCornersAndStraightRidgesReachTheVisibleMinimum() {
        assertMiniature(partFor((x, y) -> {
            if((x == 1) && (y == 0)) return 25;
            if((x == 1) && (y == 1)) return 10;
            if((x == 0) && (y == 1)) return 10;
            return 0;
        }, Coord.z));
        assertMiniature(partFor((x, y) -> {
            if((x == 1) && (y == 0)) return 25;
            if((x == 0) && (y == 1)) return 10;
            return 0;
        }, Coord.z));
        assertMiniature(partFor((x, y) -> (x == 1) ? 100 : 0, Coord.z));
    }

    @Test
    void flatComplexRidgesStayWithinTheMiniatureHeightBound() {
        assertMiniature(partFor((x, y) -> {
            if((x == 1) && (y == 0)) return 100;
            if((x == 1) && (y == 1)) return 200;
            if((x == 0) && (y == 1)) return 200;
            return 0;
        }, Coord.z));
        assertMiniature(partFor((x, y) -> {
            if((x == 1) && (y == 0)) return 100;
            if((x == 1) && (y == 1)) return 200;
            if((x == 0) && (y == 1)) return 300;
            return 0;
        }, Coord.z));
    }

    @Test
    void flatRidgeEdgesMatchAcrossAdjacentMapCuts() {
        Heights heights = (x, y) -> ((x == 1) && (y == 1)) ? 100 : 0;
        Ridges.RPart left = partFor(heights, Coord.z);
        Ridges.RPart right = partFor(heights, Coord.of(1, 0));

        List<String> leftProfile = verticalProfile(left, 11f);
        List<String> rightProfile = verticalProfile(right, 0f);
        assertTrue(leftProfile.size() >= 2, "Shared ridge edge must contain a visible vertical profile");
        assertEquals(leftProfile, rightProfile);
    }

    @Test
    void flatRidgeKeepsTheNaturalGroundCapAndOriginalWallSheet() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            NConfig.set(NConfig.Key.flatsurface, true);
            MapMesh mesh = meshFor((x, y) -> (x == 1) ? 100 : 0, Coord.z);
            Ridges ridges = mesh.data(Ridges.id);
            Ridges.RPart wall = ridges.getrdesc(Coord.z);
            Tiler.MPart[] cap = new Tiler.MPart[1];
            assertTrue(ridges.laygnd(Coord.z, (map, desc) -> cap[0] = desc));
            assertNotNull(cap[0]);
            assertEquals(4, wall.v.length, "Straight ridge keeps its original wall sheet");
            assertEquals(6, wall.f.length, "Straight ridge keeps its original wall faces");
            float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
            for(Vertex vertex : cap[0].v) {
                lowest = Math.min(lowest, vertex.z);
                highest = Math.max(highest, vertex.z);
            }
            assertEquals(0f, lowest, 0.0001f, "Ground cap must join the flat terrain");
            assertTrue(highest >= 4f && highest <= 5.0001f, "Ground cap keeps the miniature ridge lip");
        } finally {
            NConfig.current = previous;
        }
    }

    private static Ridges.RPart partFor(Heights heights, Coord ul) {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            NConfig.set(NConfig.Key.flatsurface, true);
            MapMesh mesh = meshFor(heights, ul);
            Ridges.RPart part = mesh.data(Ridges.id).getrdesc(Coord.z);
            assertNotNull(part);
            return part;
        } finally {
            NConfig.current = previous;
        }
    }

    private static void assertMiniature(Ridges.RPart part) {
        float highest = 0;
        for(Vertex vertex : part.v) {
            assertTrue(vertex.z >= -0.0001f);
            assertTrue(vertex.z <= 5.0001f);
            highest = Math.max(highest, vertex.z);
        }
        assertTrue(highest >= 4f);
    }

    private static List<String> verticalProfile(Ridges.RPart part, float x) {
        List<String> profile = new ArrayList<>();
        for(Vertex vertex : part.v) {
            if(Math.abs(vertex.x - x) < 0.0001f)
                profile.add(String.format("%.6f,%.6f", vertex.y, vertex.z));
        }
        profile.sort(Comparator.naturalOrder());
        return profile;
    }

    private static MapMesh meshFor(Heights heights, Coord ul) {
        return MapMesh.build(new RidgeMap(heights), new Random(1), ul, Coord.of(1, 1));
    }

    private interface Heights {
        double get(int x, int y);
    }

    private static class RidgeMap extends MCache {
        private final Heights heights;
        private final Tiler ridge = new RidgeTiler();

        RidgeMap(Heights heights) {
            super(null);
            this.heights = heights;
        }

        @Override
        public int gettile(Coord coord) {
            return 0;
        }

        @Override
        public Tiler tiler(int tile) {
            return ridge;
        }

        @Override
        public double getfz(Coord coord) {
            return heights.get(coord.x, coord.y);
        }
    }

    private static class RidgeTiler extends Tiler implements Ridges.RidgeTile {
        RidgeTiler() {
            super(0);
        }

        @Override
        public double breakz() {
            return 20;
        }

        @Override
        public void model(MapMesh map, Random random, Coord local, Coord global) {
            if(!map.data(Ridges.id).model(local))
                super.model(map, random, local, global);
        }

        @Override
        public void lay(MapMesh map, Coord local, Coord global, MCons cons, boolean cover) {
            super.lay(map, local, global, cons, cover);
        }

        @Override
        public void lay(MapMesh map, Random random, Coord local, Coord global) {
            lay(map, local, global, MCons.nil, false);
        }

        @Override
        public void trans(MapMesh map, Random random, Tiler ground, Coord local, Coord global,
                          int z, int borderMask, int cornerMask) {
        }
    }
}
