package nurgling.pf;

import haven.Coord;
import haven.Coord2d;
import haven.Loading;
import haven.MCache;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NPFMapCliffBufferTest {
    private static NPFMap map() {
        return new NPFMap(Coord2d.z, Coord2d.z, 1);
    }

    private static short[][] values(NPFMap map) {
        short[][] values = new short[map.size][map.size];
        for(int x = 0; x < map.size; x++)
            for(int y = 0; y < map.size; y++)
                values[x][y] = map.cells[x][y].val;
        return values;
    }

    private static void assertValues(short[][] expected, NPFMap map) {
        short[][] actual = values(map);
        for(int x = 0; x < map.size; x++)
            assertArrayEquals(expected[x], actual[x], "grid column " + x);
    }

    @Test
    void defaultAndWaterModesDoNotProbeOrAlterCells() {
        NPFMap map = map();
        short[][] before = values(map);
        assertEquals(-1, map.cliffBuffer);
        map.blockCliffs(tc -> { fail("default mode read ridge " + tc); return true; });
        assertValues(before, map);

        map.cliffBuffer = 2;
        map.waterMode = true;
        map.blockCliffs(tc -> { fail("water mode read ridge " + tc); return true; });
        assertValues(before, map);
    }

    @Test
    void enabledMaskUsesFourCornersAndNegativeTileCoordinates() {
        NPFMap map = map();
        map.cliffBuffer = 1;
        Set<Coord> ridges = Set.of(new Coord(-1, -1), new Coord(-4, -4), new Coord(2, -3));
        short[][] expected = expectedMask(map, ridges, 1);
        AtomicInteger calls = new AtomicInteger();
        map.blockCliffs(tc -> { calls.incrementAndGet(); return ridges.contains(tc); });
        assertTrue(calls.get() > 0);
        assertValues(expected, map);
        assertEquals(2, cellAt(map, Coord.z).val);
    }

    @Test
    void loadingIsUnknownWhileOtherCliffsStillBlockAndExistingBlocksStay() {
        NPFMap map = map();
        map.cliffBuffer = 0;
        NPFMap.Cell fixed = cellAt(map, new Coord(2, 2));
        fixed.val = 7;
        Set<Coord> ridges = Set.of(new Coord(0, 0));
        short[][] expected = expectedMask(map, ridges, 0);
        map.blockCliffs(tc -> {
            if(tc.equals(new Coord(-1, -1))) throw new Loading();
            return ridges.contains(tc);
        });
        assertValues(expected, map);
        assertEquals(7, fixed.val);

        NPFMap unknownOnly = map();
        unknownOnly.cliffBuffer = 0;
        short[][] unchanged = values(unknownOnly);
        unknownOnly.blockCliffs(tc -> { throw new Loading(); });
        assertValues(unchanged, unknownOnly);
    }

    @Test
    void oversizedBufferIsClampedAndEdgePaddingRemainsBounded() {
        NPFMap map = map();
        map.cliffBuffer = Integer.MAX_VALUE;
        Set<Coord> ridges = Set.of(new Coord(-39, -39), new Coord(39, 39));
        short[][] expected = expectedMask(map, ridges, 32);
        AtomicInteger calls = new AtomicInteger();
        map.blockCliffs(tc -> { calls.incrementAndGet(); return ridges.contains(tc); });
        assertTrue(calls.get() < 10_000, "tile scan should be bounded by clamped radius");
        assertValues(expected, map);
    }

    @Test
    void extremeGridCoordinatesFailBeforeChangingAnyCell() {
        NPFMap map = map();
        map.cliffBuffer = 1;
        map.cells[0][0].pos = new Coord(Integer.MIN_VALUE, Integer.MIN_VALUE);
        map.cells[map.size - 1][map.size - 1].pos = new Coord(Integer.MAX_VALUE, Integer.MAX_VALUE);
        short[][] before = values(map);
        AtomicInteger calls = new AtomicInteger();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> map.blockCliffs(tc -> { calls.incrementAndGet(); return true; }));
        assertTrue(error.getMessage().contains("Cliff scan"));
        assertEquals(0, calls.get());
        assertValues(before, map);
    }

    @Test
    void graphRoutesAroundCliffOrReportsNoCorridor() {
        Coord start = new Coord(2, 8), end = new Coord(14, 8);
        Coord ridge = new Coord(0, 0);
        NPFMap open = map();
        Graph straight = new Graph(open, start, end);
        straight.run();
        assertFalse(straight.path.isEmpty());

        NPFMap detour = map();
        detour.cliffBuffer = 0;
        detour.blockCliffs(tc -> tc.equals(ridge));
        Graph around = new Graph(detour, start, end);
        around.run();
        assertFalse(around.path.isEmpty(), "open terrain has a route around the ridge");
        assertTrue(around.getPathLen() > straight.getPathLen());
        for(Graph.Vertex vertex : around.path)
            assertEquals(0, detour.cells[vertex.i][vertex.j].val);

        NPFMap corridor = map();
        for(int x = 1; x < corridor.size - 1; x++)
            for(int y = 1; y < corridor.size - 1; y++)
                if(y != 8) corridor.cells[x][y].val = 2;
        corridor.cliffBuffer = 0;
        corridor.blockCliffs(tc -> tc.equals(ridge));
        Graph blocked = new Graph(corridor, start, end);
        blocked.run();
        assertTrue(blocked.path.isEmpty(), "cliff closes the only corridor");
    }

    private static NPFMap.Cell cellAt(NPFMap map, Coord grid) {
        Coord local = grid.sub(map.begin);
        return map.cells[local.x][local.y];
    }

    private static short[][] expectedMask(NPFMap map, Set<Coord> ridges, int radius) {
        short[][] expected = values(map);
        for(int x = 0; x < map.size; x++) {
            for(int y = 0; y < map.size; y++) {
                if(expected[x][y] != 0) continue;
                Coord2d center = Utils.pfGridToWorld(map.cells[x][y].pos);
                boolean blocked = false;
                for(int dx : new int[]{-1, 1}) {
                    for(int dy : new int[]{-1, 1}) {
                        Coord corner = center.add(dx * MCache.tileqsz.x, dy * MCache.tileqsz.y)
                                .floor(MCache.tilesz);
                        for(Coord ridge : ridges)
                            if(Math.abs((long) corner.x - ridge.x) <= radius
                                    && Math.abs((long) corner.y - ridge.y) <= radius)
                                blocked = true;
                    }
                }
                if(blocked) expected[x][y] = 2;
            }
        }
        return expected;
    }
}
