package haven;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordZAliasTest {
    @AfterEach
    void restoreOrigin() {
        Coord.z.x = 0;
        Coord.z.y = 0;
    }

    @Test
    void overlayGridAreaIgnoresPollutedCoordZ() {
        Coord.z.x = 0;
        Coord.z.y = 1;
        Area a = Area.sized(Coord.z, MCache.cmaps);
        assertEquals(0, a.ul.x);
        assertEquals(0, a.ul.y);
        assertEquals(100, a.br.x);
        assertEquals(100, a.br.y);
        int max = -1;
        int n = 0;
        for (Coord c : a) {
            int i = c.x + (c.y * MCache.cmaps.x);
            assertTrue(i >= 0 && i < 10000, () -> "overlay index " + i);
            if (i > max)
                max = i;
            n++;
        }
        assertEquals(10000, n);
        assertEquals(9999, max);
    }

    @Test
    void sizedAreaDoesNotKeepCoordZReference() {
        Area a = Area.sized(Coord.z, MCache.cmaps);
        Coord.z.x = 1;
        Coord.z.y = 0;
        assertEquals(0, a.ul.x);
        assertEquals(0, a.ul.y);
        assertNotSame(Coord.z, a.ul);
    }

    @Test
    void widgetDoesNotAliasCoordZ() {
        Widget w = new Widget();
        assertNotSame(Coord.z, w.c);
        assertNotSame(Coord.z, w.sz);
        w.c.x = 40;
        w.sz.x = 50;
        assertEquals(0, Coord.z.x);
        assertEquals(0, Coord.z.y);
    }
}
