package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NIconDockTest {

    @Test
    void hiddenUntilTheCursorComesNear() {
        NIconDock dock = new NIconDock();
        Coord sz = new Coord(200, 200);

        for(int i = 0; i < 100; i++)
            dock.track(new Coord(1000, 1000), sz, 0.05);
        assertTrue(dock.hidden(), "icons must stay hidden while the cursor is elsewhere");

        for(int i = 0; i < 100; i++)
            dock.track(new Coord(100, 100), sz, 0.05);
        assertFalse(dock.hidden(), "icons must appear once the cursor is over the panel");
    }

    @Test
    void revealRampsOverTheFadeDistance() {
        assertEquals(1.0, NIconDock.reveal(0));
        assertEquals(0.0, NIconDock.reveal(NIconDock.FADE));
        assertEquals(0.0, NIconDock.reveal(NIconDock.FADE * 4));
        assertTrue(NIconDock.reveal(NIconDock.FADE / 2) > 0);
        assertTrue(NIconDock.reveal(NIconDock.FADE / 4) > NIconDock.reveal(NIconDock.FADE / 2));
    }

    @Test
    void nearerIconsGrowMore() {
        assertEquals(1.0 + NIconDock.GROW, NIconDock.scale(0));
        assertEquals(1.0, NIconDock.scale(NIconDock.REACH));
        assertEquals(1.0, NIconDock.scale(NIconDock.REACH * 2));
        double near = NIconDock.scale(NIconDock.REACH * 0.25);
        double mid = NIconDock.scale(NIconDock.REACH * 0.5);
        double far = NIconDock.scale(NIconDock.REACH * 0.75);
        assertTrue(near > mid && mid > far && far > 1.0, "the size must fall off in steps");
    }

    @Test
    void grownIconIsTheBrightest() {
        NIconDock dock = new NIconDock();
        Coord sz = new Coord(200, 200);
        for(int i = 0; i < 100; i++)
            dock.track(new Coord(100, 100), sz, 0.05);
        assertTrue(dock.alpha(1.0 + NIconDock.GROW) > dock.alpha(1.0));
        assertTrue(dock.alpha(1.0) > 0, "a revealed icon is dim, not invisible");
        assertTrue(dock.alpha(1.0 + NIconDock.GROW) <= 255);
    }

    @Test
    void insideThePanelCountsAsZeroDistance() {
        Coord sz = new Coord(200, 100);
        assertEquals(0.0, NIconDock.rectdist(new Coord(10, 10), sz));
        assertEquals(0.0, NIconDock.rectdist(new Coord(200, 100), sz));
        assertEquals(10.0, NIconDock.rectdist(new Coord(210, 50), sz));
        assertEquals(10.0, NIconDock.rectdist(new Coord(50, -10), sz));
    }
}
