package nurgling.map;

import haven.Coord;
import nurgling.navigation.ChunkNavData;
import nurgling.navigation.ChunkPortal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static haven.MCache.cmaps;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorOverlayAlignerTest {

    private static final long SURFACE = 10L;
    private static final long MINE = 20L;
    private static final long DEEPER = 30L;

    @Test
    void offsetUsesPortalTilesWhenExitCoordIsKnown() {
        FloorOverlayAligner.GridRef src = new FloorOverlayAligner.GridRef(SURFACE, new Coord(3, 4));
        FloorOverlayAligner.GridRef dst = new FloorOverlayAligner.GridRef(MINE, new Coord(1, 2));
        Coord local = new Coord(10, 20);
        Coord exit = new Coord(30, 40);

        Coord offset = FloorOverlayAligner.computeOffset(src, local, dst, exit);

        Coord srcTc = src.sc.mul(cmaps).add(local);
        Coord dstTc = dst.sc.mul(cmaps).add(exit);
        assertEquals(srcTc.sub(dstTc), offset);
        assertEquals(srcTc, dstTc.add(offset));
    }

    @Test
    void offsetFallsBackToChunkOriginsWhenExitCoordMissing() {
        FloorOverlayAligner.GridRef src = new FloorOverlayAligner.GridRef(SURFACE, new Coord(5, 1));
        FloorOverlayAligner.GridRef dst = new FloorOverlayAligner.GridRef(MINE, new Coord(2, 8));

        Coord offset = FloorOverlayAligner.computeOffset(src, new Coord(10, 10), dst, null);

        assertEquals(src.sc.sub(dst.sc).mul(cmaps), offset);
    }

    @Test
    void destTileMapsOntoSourceSegment() {
        FloorOverlayAligner.FloorLink link = new FloorOverlayAligner.FloorLink(
                SURFACE, MINE, new Coord(50, -20), true, 4);
        assertEquals(new Coord(150, 80), link.destTileToSrc(new Coord(100, 100)));
    }

    @Test
    void groupsConnectedMineSegmentFromSurface() {
        TestLookup lookup = new TestLookup();
        lookup.put(1L, SURFACE, new Coord(0, 0));
        lookup.put(2L, MINE, new Coord(0, 0));

        ChunkNavData surface = chunk(1L, caveIn(2L, new Coord(40, 40), new Coord(41, 42)));
        ChunkNavData mine = chunk(2L, caveOut(1L, new Coord(41, 42), new Coord(40, 40)));

        List<FloorOverlayAligner.FloorLink> links = FloorOverlayAligner.linksFrom(
                SURFACE, list(surface, mine), lookup);

        assertEquals(1, links.size());
        FloorOverlayAligner.FloorLink link = links.get(0);
        assertEquals(MINE, link.toSegId);
        assertTrue(link.destIsBelow);
        assertEquals(1, link.destChunkCount);
        Coord expected = FloorOverlayAligner.computeOffset(
                lookup.find(1L), new Coord(40, 40), lookup.find(2L), new Coord(41, 42));
        assertEquals(expected, link.tileOffset);
    }

    @Test
    void firstPortalWinsWhenOffsetsDisagree() {
        TestLookup lookup = new TestLookup();
        lookup.put(1L, SURFACE, new Coord(0, 0));
        lookup.put(2L, SURFACE, new Coord(1, 0));
        lookup.put(3L, MINE, new Coord(0, 0));
        lookup.put(4L, MINE, new Coord(0, 1));

        ChunkPortal first = caveIn(3L, new Coord(10, 10), new Coord(10, 10));
        ChunkPortal disagree = caveIn(4L, new Coord(10, 10), new Coord(80, 80));

        List<FloorOverlayAligner.FloorLink> links = FloorOverlayAligner.linksFrom(
                SURFACE,
                list(chunk(1L, first), chunk(2L, disagree), chunk(3L), chunk(4L)),
                lookup);

        assertEquals(1, links.size());
        Coord expected = FloorOverlayAligner.computeOffset(
                lookup.find(1L), new Coord(10, 10), lookup.find(3L), new Coord(10, 10));
        assertEquals(expected, links.get(0).tileOffset);
    }

    @Test
    void composesOffsetAcrossTwoMineLevels() {
        TestLookup lookup = new TestLookup();
        lookup.put(1L, SURFACE, new Coord(0, 0));
        lookup.put(2L, MINE, new Coord(0, 0));
        lookup.put(3L, DEEPER, new Coord(0, 0));

        Coord off1 = FloorOverlayAligner.computeOffset(
                lookup.find(1L), new Coord(5, 5), lookup.find(2L), new Coord(7, 8));
        Coord off2 = FloorOverlayAligner.computeOffset(
                lookup.find(2L), new Coord(7, 8), lookup.find(3L), new Coord(1, 2));

        List<FloorOverlayAligner.FloorLink> links = FloorOverlayAligner.linksFrom(
                SURFACE,
                list(
                        chunk(1L, caveIn(2L, new Coord(5, 5), new Coord(7, 8))),
                        chunk(2L, caveIn(3L, new Coord(7, 8), new Coord(1, 2))),
                        chunk(3L)),
                lookup);

        assertEquals(2, links.size());
        FloorOverlayAligner.FloorLink deeper = links.stream()
                .filter(l -> l.toSegId == DEEPER)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing deeper floor"));
        assertEquals(off1.add(off2), deeper.tileOffset);
        assertTrue(deeper.destIsBelow);
    }

    @Test
    void ignoresDoorsAndUnlinkedPortals() {
        TestLookup lookup = new TestLookup();
        lookup.put(1L, SURFACE, new Coord(0, 0));
        lookup.put(2L, MINE, new Coord(0, 0));

        ChunkPortal door = new ChunkPortal("d", "gfx/terobjs/door", ChunkPortal.PortalType.DOOR, new Coord(1, 1));
        door.connectsToGridId = 2L;
        door.exitLocalCoord = new Coord(1, 1);
        ChunkPortal unlinked = caveIn(-1L, new Coord(2, 2), new Coord(2, 2));

        List<FloorOverlayAligner.FloorLink> links = FloorOverlayAligner.linksFrom(
                SURFACE, list(chunk(1L, door, unlinked), chunk(2L)), lookup);

        assertTrue(links.isEmpty());
    }

    @Test
    void visibleDestGridAreaOnlyCoversViewport() {
        haven.Area currentTiles = haven.Area.sized(Coord.z, new Coord(250, 250));
        haven.Area dest = FloorOverlayAligner.visibleDestGridArea(currentTiles, Coord.z, 0);
        assertTrue(dest.contains(new Coord(0, 0)));
        assertTrue(dest.contains(new Coord(2, 2)));
        assertFalse(dest.contains(new Coord(40, 40)));
        assertTrue(dest.area() <= 16, "viewport must not scan the whole segment: " + dest.area());
    }

    @Test
    void ladderFromMineIsAbove() {
        TestLookup lookup = new TestLookup();
        lookup.put(1L, MINE, new Coord(0, 0));
        lookup.put(2L, SURFACE, new Coord(4, 4));

        ChunkPortal ladder = new ChunkPortal("l", "gfx/terobjs/ladder", ChunkPortal.PortalType.LADDER, new Coord(20, 20));
        ladder.connectsToGridId = 2L;
        ladder.exitLocalCoord = new Coord(21, 21);

        List<FloorOverlayAligner.FloorLink> links = FloorOverlayAligner.linksFrom(
                MINE, list(chunk(1L, ladder), chunk(2L)), lookup);

        assertEquals(1, links.size());
        assertFalse(links.get(0).destIsBelow);
        assertEquals(SURFACE, links.get(0).toSegId);
    }

    private static ChunkNavData chunk(long gridId, ChunkPortal... portals) {
        ChunkNavData data = new ChunkNavData(gridId);
        for (ChunkPortal portal : portals) {
            data.portals.add(portal);
        }
        return data;
    }

    private static ChunkPortal caveIn(long toGrid, Coord local, Coord exit) {
        ChunkPortal portal = new ChunkPortal("in-" + toGrid, "gfx/tiles/ridges/cavein", ChunkPortal.PortalType.CAVEIN, local);
        portal.connectsToGridId = toGrid;
        portal.exitLocalCoord = exit;
        return portal;
    }

    private static ChunkPortal caveOut(long toGrid, Coord local, Coord exit) {
        ChunkPortal portal = new ChunkPortal("out-" + toGrid, "gfx/tiles/ridges/caveout", ChunkPortal.PortalType.CAVEOUT, local);
        portal.connectsToGridId = toGrid;
        portal.exitLocalCoord = exit;
        return portal;
    }

    @SafeVarargs
    private static <T> List<T> list(T... items) {
        List<T> out = new ArrayList<>();
        for (T item : items) {
            out.add(item);
        }
        return out;
    }

    private static final class TestLookup implements FloorOverlayAligner.GridLookup {
        private final Map<Long, FloorOverlayAligner.GridRef> grids = new HashMap<>();
        private final Map<Long, Integer> counts = new HashMap<>();

        void put(long gridId, long segId, Coord sc) {
            grids.put(gridId, new FloorOverlayAligner.GridRef(segId, sc));
            counts.put(segId, counts.getOrDefault(segId, 0) + 1);
        }

        @Override
        public FloorOverlayAligner.GridRef find(long gridId) {
            return grids.get(gridId);
        }

        @Override
        public int segmentGridCount(long segId) {
            return counts.getOrDefault(segId, 0);
        }
    }
}
