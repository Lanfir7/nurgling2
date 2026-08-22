package nurgling.actions;

import nurgling.NHitBox;
import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PileMakerTest {
    @Test
    void soilHitboxWorksWithoutLoadedPlobGhost() {
        NHitBox box = PileMaker.resolveHitbox(null, new NAlias("gfx/terobjs/stockpile-soil"));
        assertNotNull(box);
        assertEquals(NHitBox.findCustom("gfx/terobjs/stockpile-soil").begin, box.begin);
        assertEquals(NHitBox.findCustom("gfx/terobjs/stockpile-soil").end, box.end);
    }

    @Test
    void prefersReadyPlobHitbox() {
        NHitBox fromPlob = new NHitBox(new haven.Coord(-1, -1), new haven.Coord(1, 1), true);
        assertSame(fromPlob, PileMaker.resolveHitbox(fromPlob, new NAlias("gfx/terobjs/stockpile-soil")));
    }

    @Test
    void unknownPileFallsBackToGenericStockpile() {
        NHitBox box = PileMaker.resolveHitbox(null, new NAlias("stockpile"));
        assertNotNull(box);
        assertEquals(NHitBox.findCustom("stockpile").begin, box.begin);
    }
}
