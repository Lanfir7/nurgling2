package nurgling;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootNoticeLayoutTest {
    @Test
    void defaultFollowsCompassBottomAndCentersOnScreen() {
        LootNoticeLayout layout = new LootNoticeLayout(true, 4, 96, 90);
        assertEquals(Coord.of(810, 192), layout.origin(Coord.of(1920, 1080), Coord.of(300, 57),
                7, 2, 180, 12, 110));
        assertEquals(Coord.of(810, 110), layout.origin(Coord.of(1920, 1080), Coord.of(300, 57),
                7, 2, null, 12, 110));
    }

    @Test
    void customPercentagesSpanUsableScreenAndClampWholeStack() {
        LootNoticeLayout topLeft = new LootNoticeLayout(false, 0, 0, 80);
        LootNoticeLayout bottomRight = new LootNoticeLayout(false, 100, 100, 80);
        assertEquals(Coord.z, topLeft.origin(Coord.of(1920, 1080), Coord.of(300, 57), 7, 4, null, 12, 110));
        assertEquals(Coord.of(1620, 831), bottomRight.origin(Coord.of(1920, 1080), Coord.of(300, 57),
                7, 4, null, 12, 110));
        assertEquals(1, LootNoticeLayout.visibleRows(120, 57, 7, 4));
        assertEquals(0, LootNoticeLayout.visibleRows(40, 57, 7, 4));
        assertEquals(Coord.of(10, 63), new LootNoticeLayout(true, 50, 12, 90)
                .origin(Coord.of(320, 120), Coord.of(300, 57), 7, 1, 200, 12, 110));
    }

    @Test
    void invalidSavedValuesUseDefaultsOrNearestBound() {
        assertEquals(90, LootNoticeLayout.percent(Double.NaN, 90));
        assertEquals(50, LootNoticeLayout.percent("oops", 50));
        assertEquals(100, LootNoticeLayout.percent(210, 50));
        assertEquals(0, LootNoticeLayout.percent(-80, 50));
        LootNoticeLayout layout = new LootNoticeLayout(false, -5, 140, 250);
        assertEquals(0, layout.xPercent);
        assertEquals(100, layout.yPercent);
        assertEquals(100, layout.backgroundOpacity);
    }
}
