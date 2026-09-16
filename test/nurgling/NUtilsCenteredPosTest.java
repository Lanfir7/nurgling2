package nurgling;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NUtilsCenteredPosTest {
    @Test
    void centersChildOnParentRegardlessOfResolution() {
        assertEquals(new Coord(860, 440),
                NUtils.centeredPos(new Coord(1920, 1080), new Coord(200, 200)));
        assertEquals(new Coord(1720, 980),
                NUtils.centeredPos(new Coord(3840, 2160), new Coord(400, 200)));
        assertEquals(new Coord(412, 234),
                NUtils.centeredPos(new Coord(1366, 768), new Coord(542, 300)));
    }

    @Test
    void clampsToOriginWhenChildIsLargerThanParent() {
        assertEquals(Coord.z, NUtils.centeredPos(new Coord(100, 100), new Coord(400, 300)));
        assertEquals(new Coord(50, 0), NUtils.centeredPos(new Coord(200, 50), new Coord(100, 80)));
    }

    @Test
    void nullSizesStayAtOrigin() {
        assertEquals(Coord.z, NUtils.centeredPos(null, new Coord(10, 10)));
        assertEquals(Coord.z, NUtils.centeredPos(new Coord(10, 10), null));
    }

    @Test
    void botWindowsRememberPositionByClass() {
        assertEquals("botwnd-nurgling.widgets.bots.ChipperWnd",
                NUtils.rememberedWindowKey("nurgling.widgets.bots.ChipperWnd"));
        assertEquals("botwnd-nurgling.widgets.bots.Forager",
                NUtils.rememberedWindowKey("nurgling.widgets.bots.Forager"));
        assertNotEquals(
                NUtils.rememberedWindowKey("nurgling.widgets.bots.ChipperWnd"),
                NUtils.rememberedWindowKey("nurgling.widgets.bots.Chopper"));
    }

    @Test
    void nonBotWindowsDoNotRemember() {
        assertNull(NUtils.rememberedWindowKey("nurgling.widgets.TextInputWindow"));
        assertNull(NUtils.rememberedWindowKey("haven.Window"));
        assertNull(NUtils.rememberedWindowKey((String) null));
        assertNull(NUtils.rememberedWindowKey("nurgling.widgets.bots.ChipperWnd$1"));
    }

    @Test
    void masterMinerKeepsOwnWindowPersistence() {
        assertNull(NUtils.rememberedWindowKey("nurgling.widgets.bots.MasterMinerWnd"));
    }

    @Test
    void savedPositionWinsOverCenter() {
        assertEquals(new Coord(40, 80),
                NUtils.windowPlacementPos(new Coord(1920, 1080), new Coord(200, 200), new Coord(40, 80)));
        assertEquals(new Coord(860, 440),
                NUtils.windowPlacementPos(new Coord(1920, 1080), new Coord(200, 200), null));
    }
}
