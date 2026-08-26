package nurgling.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreatureHpTest {
    @ParameterizedTest
    @CsvSource({
            "gfx/kritter/boar/boar, 450",
            "gfx/invobjs/kritter/wildboar, 450",
            "gfx/kritter/bear/bear, 850",
            "gfx/kritter/bear/polarbear, 1250",
            "gfx/kritter/wolf/wolf, 500",
            "gfx/kritter/fox/fox, 110",
            "gfx/kritter/adder/adder, 70",
            "gfx/kritter/goat/wildgoat, 300",
            "gfx/kritter/lynx/lynx, 400",
            "gfx/kritter/moose/moose, 800",
            "gfx/kritter/troll/troll, 1000",
            "gfx/kritter/walrus/walrus, 900",
            "gfx/kritter/mammoth/mammoth, 4000",
            "gfx/kritter/cavelouse/cavelouse, 1000",
            "gfx/kritter/ooze/greenooze, 60",
            "gfx/kritter/rat/caverat, 120",
    })
    void maxHpFromGobPath(String resName, int hp) {
        assertEquals(Integer.valueOf(hp), CreatureHp.maxHp(resName));
    }

    @Test
    void unknownAndEmptyHaveNoMax() {
        assertNull(CreatureHp.maxHp("gfx/borka/body"));
        assertNull(CreatureHp.maxHp("gfx/kritter/nidbane/nidbane"));
        assertNull(CreatureHp.maxHp(null));
        assertNull(CreatureHp.maxHp(""));
    }

    @Test
    void labelWithMax() {
        assertEquals("45/110", CreatureHp.label(45, "gfx/kritter/fox/fox"));
        assertEquals("0/850", CreatureHp.label(0, "gfx/kritter/bear/bear"));
    }

    @Test
    void labelWithoutMaxShowsDealtOnly() {
        assertEquals("45", CreatureHp.label(45, "gfx/borka/body"));
        assertNull(CreatureHp.label(0, "gfx/borka/body"));
        assertNull(CreatureHp.label(0, null));
    }
}
