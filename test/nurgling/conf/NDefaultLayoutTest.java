package nurgling.conf;

import haven.Coord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the default HUD layouts against the two failure modes that made the
 * old defaults feel broken: widgets overlapping each other, and widgets sitting
 * partly off-screen. The nominal footprints used by the preview are the design
 * intent, so checking them here catches a bad offset without launching the game.
 */
class NDefaultLayoutTest {
    private static final int BELTS = 3;

    private static List<NDefaultLayout.Preview> layout(NDefaultLayout.Preset preset) {
        return (NDefaultLayout.preview(preset, NDefaultLayout.design, BELTS));
    }

    @ParameterizedTest
    @EnumSource(NDefaultLayout.Preset.class)
    void staysOnScreen(NDefaultLayout.Preset preset) {
        Coord screen = NDefaultLayout.design;
        for (NDefaultLayout.Preview p : layout(preset)) {
            assertTrue((p.c.x >= 0) && (p.c.y >= 0),
                       preset + ": widget at " + p.c + " starts off-screen");
            assertTrue(((p.c.x + p.sz.x) <= screen.x) && ((p.c.y + p.sz.y) <= screen.y),
                       preset + ": widget at " + p.c + " size " + p.sz + " runs past " + screen);
        }
    }

    @ParameterizedTest
    @EnumSource(NDefaultLayout.Preset.class)
    void hasNoOverlaps(NDefaultLayout.Preset preset) {
        List<NDefaultLayout.Preview> all = layout(preset);
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                NDefaultLayout.Preview a = all.get(i), b = all.get(j);
                boolean isect = (a.c.x < (b.c.x + b.sz.x)) && (b.c.x < (a.c.x + a.sz.x)) &&
                                (a.c.y < (b.c.y + b.sz.y)) && (b.c.y < (a.c.y + a.sz.y));
                assertFalse(isect, preset + ": " + a.c + "/" + a.sz +
                                   " overlaps " + b.c + "/" + b.sz);
            }
        }
    }

    @Test
    void minimalHidesMoreThanClassic() {
        assertTrue(layout(NDefaultLayout.Preset.MINIMAL).size()
                   < layout(NDefaultLayout.Preset.CLASSIC).size(),
                   "MINIMAL should show fewer widgets than CLASSIC");
    }

    @Test
    void compassHasVisibleTopCenterSlot() {
        NDefaultLayout.Slot slot = NDefaultLayout.slot(NDefaultLayout.Preset.CLASSIC, "compass");
        assertNotNull(slot);
        assertEquals(NDefaultLayout.Anchor.TC, slot.anchor);
        assertTrue(slot.vis);
    }
}
