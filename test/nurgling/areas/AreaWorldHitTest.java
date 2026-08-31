package nurgling.areas;

import haven.Coord2d;
import haven.Pair;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaWorldHitTest {
    @Test
    void pointInsideAreaAttributedWhenLabelsNotClickable() {
        assertFalse(AreaLabelSync.labelsClickable(false));
        BoxedArea area = boxed(1, 0, 0, 10, 10);
        assertSame(area, AreaWorldHit.smallestContaining(List.of(area), new Coord2d(5, 5)));
        assertTrue(area.checkHit(new Coord2d(5, 5)));
    }

    @Test
    void pointOutsideReturnsNull() {
        BoxedArea area = boxed(1, 0, 0, 10, 10);
        assertNull(AreaWorldHit.smallestContaining(List.of(area), new Coord2d(20, 20)));
    }

    @Test
    void overlappingAreasPickSmallest() {
        BoxedArea big = boxed(1, 0, 0, 100, 100);
        BoxedArea small = boxed(2, 40, 40, 60, 60);
        assertSame(small, AreaWorldHit.smallestContaining(List.of(big, small), new Coord2d(50, 50)));
        assertSame(small, AreaWorldHit.smallestContaining(List.of(small, big), new Coord2d(50, 50)));
    }

    @Test
    void gobWithActionsWinsOverArea() {
        assertEquals(AreaWorldHit.Kind.GOB,
                AreaWorldHit.decide(false, true, true, true));
    }

    @Test
    void areaWinsOverTileWhenNoGobActions() {
        assertEquals(AreaWorldHit.Kind.AREA,
                AreaWorldHit.decide(false, false, true, true));
    }

    @Test
    void boundaryGobGoesToServerEvenInsideArea() {
        assertEquals(AreaWorldHit.Kind.BOUNDARY,
                AreaWorldHit.decide(true, true, true, true));
    }

    @Test
    void tileOrServerWhenMiss() {
        assertEquals(AreaWorldHit.Kind.TILE,
                AreaWorldHit.decide(false, false, false, true));
        assertEquals(AreaWorldHit.Kind.SERVER,
                AreaWorldHit.decide(false, false, false, false));
    }

    @Test
    void resolveGobWinsOverContainingArea() {
        BoxedArea area = boxed(1, 0, 0, 10, 10);
        AreaWorldHit.Hit hit = AreaWorldHit.resolve(false, true, List.of(area), new Coord2d(5, 5), true);
        assertEquals(AreaWorldHit.Kind.GOB, hit.kind);
        assertNull(hit.area);
    }

    @Test
    void resolvePicksSmallestAreaWhenNoGob() {
        BoxedArea big = boxed(1, 0, 0, 100, 100);
        BoxedArea small = boxed(2, 40, 40, 60, 60);
        AreaWorldHit.Hit hit = AreaWorldHit.resolve(false, false, List.of(big, small), new Coord2d(50, 50), true);
        assertEquals(AreaWorldHit.Kind.AREA, hit.kind);
        assertSame(small, hit.area);
    }

    @Test
    void resolveMissUsesTileThenServer() {
        BoxedArea area = boxed(1, 0, 0, 10, 10);
        assertEquals(AreaWorldHit.Kind.TILE,
                AreaWorldHit.resolve(false, false, List.of(area), new Coord2d(99, 99), true).kind);
        assertEquals(AreaWorldHit.Kind.SERVER,
                AreaWorldHit.resolve(false, false, List.of(area), new Coord2d(99, 99), false).kind);
    }

    @Test
    void mapViewCtrlRmbUsesHitTestAndSameOptsAtMenu() throws Exception {
        String src = new String(Files.readAllBytes(Paths.get("src/nurgling/NMapView.java")), StandardCharsets.UTF_8);
        assertTrue(src.contains("AreaWorldHit.resolve"), src);
        assertTrue(src.contains("openAreaOptsAt"), src);
        String widget = new String(Files.readAllBytes(Paths.get("src/nurgling/widgets/NAreasWidget.java")), StandardCharsets.UTF_8);
        assertTrue(widget.contains("found.optsAt(rootPos)"), widget);
        assertTrue(widget.contains("showPath(path, area.id)"), widget);
    }

    private static BoxedArea boxed(int id, double x1, double y1, double x2, double y2) {
        return new BoxedArea(id, new Pair<>(new Coord2d(x1, y1), new Coord2d(x2, y2)));
    }

    static final class BoxedArea extends NArea {
        final Pair<Coord2d, Coord2d> box;

        BoxedArea(int id, Pair<Coord2d, Coord2d> box) {
            super("a" + id);
            this.id = id;
            this.box = box;
        }

        @Override
        public Pair<Coord2d, Coord2d> getRCArea() {
            return box;
        }
    }
}
