package nurgling;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
