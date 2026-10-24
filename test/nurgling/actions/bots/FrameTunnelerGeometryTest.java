package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.MCache;
import nurgling.overlays.NMiningSupport;
import nurgling.widgets.TunnelingDialog.Direction;
import nurgling.widgets.TunnelingDialog.SupportType;
import nurgling.widgets.TunnelingDialog.TunnelSide;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameTunnelerGeometryTest {
    private static final int FRONTIER = -20;

    @Test
    void previewAndBuiltFrameCoverTheSameLastTileInAllDirections() throws Exception {
        for(Direction direction : Direction.values()) {
            for(SupportType type : Arrays.asList(SupportType.TIMBER_TUNNEL,
                    SupportType.REINFORCED_TUNNEL, SupportType.STONE_ARCH_TUNNEL)) {
                FrameTunneler tunnel = corridor(direction, type, false);
                int[] columns = columns(tunnel, "frameCols");
                int length = type.frameSpec().lengthTiles;
                Coord origin = solveOrigin(tunnel, FRONTIER);
                assertNotNull(origin, direction + " " + type);

                Coord2d previewAnchor = frameAnchor(tunnel, origin);
                double angle = angle(direction);
                Set<Coord> preview = tiles(NMiningSupport.computeRect(previewAnchor, angle,
                        columns.length, length, false));
                Set<Coord> wanted = new HashSet<>();
                for(int row = FRONTIER; row < FRONTIER + length; row++) {
                    for(int col : columns) wanted.add(tile(direction, row, col));
                }
                assertEquals(wanted, preview, direction + " " + type + " preview");

                // The server places a completed frame just behind the preview's tile edge.
                Coord2d builtAnchor = previewAnchor.sub(Coord2d.of(direction.dx, direction.dy)
                        .mul(0.1));
                Set<Coord> built = tiles(NMiningSupport.computeRect(builtAnchor, angle,
                        columns.length, length, true));
                assertEquals(preview, built, direction + " " + type + " built frame");
                assertEquals(columns.length * length, built.size());
                for(int col : columns) {
                    assertTrue(built.contains(tile(direction, FRONTIER + length - 1, col)),
                            direction + " " + type + " last protected tile");
                    assertFalse(built.contains(tile(direction, FRONTIER + length, col)),
                            direction + " " + type + " first unprotected tile");
                }
            }
        }
    }

    @Test
    void centreLineOnlyChangesDigColumnsNotFrameCoverage() throws Exception {
        for(Direction direction : Direction.values()) {
            for(SupportType type : Arrays.asList(SupportType.REINFORCED_TUNNEL,
                    SupportType.STONE_ARCH_TUNNEL)) {
                FrameTunneler full = corridor(direction, type, false);
                FrameTunneler centre = corridor(direction, type, true);
                int[] frameColumns = columns(full, "frameCols");
                assertEquals(type.frameSpec().widthTiles, frameColumns.length);
                assertTrue(Arrays.equals(frameColumns, columns(full, "digCols")));
                assertTrue(Arrays.equals(frameColumns, columns(centre, "frameCols")));
                int[] digColumns = columns(centre, "digCols");
                assertEquals(1, digColumns.length);
                assertTrue(Arrays.stream(frameColumns).anyMatch(c -> c == digColumns[0]));
                assertEquals(solveOrigin(full, FRONTIER), solveOrigin(centre, FRONTIER));
            }
        }
    }

    private static FrameTunneler corridor(Direction direction, SupportType type,
                                           boolean centreLine) throws Exception {
        FrameTunneler tunnel = new FrameTunneler(direction, type, TunnelSide.SOUTH, centreLine);
        Coord playerTile = tile(direction, -23, -17);
        Gob unrelatedSupport = new Gob(null, playerTile.mul(MCache.tilesz).add(MCache.tilehsz), 42);
        unrelatedSupport.ngob.name = "gfx/terobjs/other";
        Method setup = FrameTunneler.class.getDeclaredMethod("setUpCorridor", Gob.class, Coord.class);
        setup.setAccessible(true);
        setup.invoke(tunnel, unrelatedSupport, playerTile);
        return tunnel;
    }

    private static int[] columns(FrameTunneler tunnel, String name) throws Exception {
        Field field = FrameTunneler.class.getDeclaredField(name);
        field.setAccessible(true);
        return (int[]) field.get(tunnel);
    }

    private static Coord solveOrigin(FrameTunneler tunnel, int frontier) throws Exception {
        Method solve = FrameTunneler.class.getDeclaredMethod("solveOrigin", int.class);
        solve.setAccessible(true);
        return (Coord) solve.invoke(tunnel, frontier);
    }

    private static Coord2d frameAnchor(FrameTunneler tunnel, Coord origin) throws Exception {
        Method anchor = FrameTunneler.class.getDeclaredMethod("frameAnchor", Coord.class);
        anchor.setAccessible(true);
        return (Coord2d) anchor.invoke(tunnel, origin);
    }

    @SuppressWarnings("unchecked")
    private static Set<Coord> tiles(NMiningSupport.Mask mask) throws Exception {
        Method tiles = FrameTunneler.class.getDeclaredMethod("tilesOf", NMiningSupport.Mask.class);
        tiles.setAccessible(true);
        return new HashSet<>((List<Coord>) tiles.invoke(null, mask));
    }

    private static Coord tile(Direction direction, int row, int col) {
        Coord forward = new Coord(direction.dx, direction.dy);
        Coord lateral = new Coord(Math.abs(direction.dy), Math.abs(direction.dx));
        return forward.mul(row).add(lateral.mul(col));
    }

    private static double angle(Direction direction) {
        switch(direction) {
            case EAST: return 0;
            case SOUTH: return Math.PI / 2;
            case WEST: return Math.PI;
            default: return 3 * Math.PI / 2;
        }
    }
}
