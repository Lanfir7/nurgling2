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

    @Test
    void armorGreenDoesNotCountAsHp() {
        assertEquals(45, CreatureHp.hpDealt(45, 0, 20));
        assertEquals(55, CreatureHp.hpDealt(45, 10, 20));
        assertEquals(0, CreatureHp.hpDealt(0, 0, 50));
    }

    @Test
    void remainingHpIsMaxMinusDealt() {
        assertEquals(65, CreatureHp.remaining(45, 110));
        assertEquals(0, CreatureHp.remaining(200, 110));
        assertEquals(110, CreatureHp.remaining(0, 110));
    }

    @Test
    void remainingLabel() {
        assertEquals("65/110", CreatureHp.remainingLabel(45, "gfx/kritter/fox/fox"));
        assertEquals("850/850", CreatureHp.remainingLabel(0, "gfx/kritter/bear/bear"));
        assertEquals("45", CreatureHp.remainingLabel(45, "gfx/borka/body"));
        assertNull(CreatureHp.remainingLabel(0, "gfx/borka/body"));
    }

    @Test
    void remainingFraction() {
        assertEquals(1.0f, CreatureHp.fraction(0, 100), 0.001f);
        assertEquals(0.5f, CreatureHp.fraction(50, 100), 0.001f);
        assertEquals(0.0f, CreatureHp.fraction(100, 100), 0.001f);
        assertEquals(0.0f, CreatureHp.fraction(150, 100), 0.001f);
    }
}
