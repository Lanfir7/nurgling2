package nurgling.widgets.nsettings;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForagerRouteMapWindowTest {
    @Test
    void innerSizeNeverGoesBelowTheLargeMapMinimum() {
        Coord min = ForagerRouteMapWindow.MIN_INNER;
        assertEquals(min, ForagerRouteMapWindow.clampInnerSize(new Coord(100, 80)));
        assertEquals(min, ForagerRouteMapWindow.clampInnerSize(min));
    }

    @Test
    void largerInnerSizeIsKept() {
        Coord asked = new Coord(800, 600);
        assertEquals(asked, ForagerRouteMapWindow.clampInnerSize(asked));
    }

    @Test
    void mapFillsTheClampedInnerArea() {
        Coord inner = ForagerRouteMapWindow.clampInnerSize(new Coord(900, 700));
        assertEquals(inner, ForagerRouteMapWindow.mapSizeForInner(inner));
    }
}
