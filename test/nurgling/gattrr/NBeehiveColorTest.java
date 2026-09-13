package nurgling.gattrr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NBeehiveColorTest {
    @Test
    void honeyOnlyIsHoney() {
        assertEquals(NBeehiveColor.Kind.HONEY, NBeehiveColor.kindOf(35));
    }

    @Test
    void waxOnlyIsWax() {
        assertEquals(NBeehiveColor.Kind.WAX, NBeehiveColor.kindOf(6));
    }

    @Test
    void honeyAndWaxIsBoth() {
        assertEquals(NBeehiveColor.Kind.BOTH, NBeehiveColor.kindOf(39));
    }

    @Test
    void emptyIsEmpty() {
        assertEquals(NBeehiveColor.Kind.EMPTY, NBeehiveColor.kindOf(0));
        assertEquals(NBeehiveColor.Kind.EMPTY, NBeehiveColor.kindOf(2));
        assertEquals(NBeehiveColor.Kind.EMPTY, NBeehiveColor.kindOf(8));
        assertEquals(NBeehiveColor.Kind.EMPTY, NBeehiveColor.kindOf(16));
        assertEquals(NBeehiveColor.Kind.EMPTY, NBeehiveColor.kindOf(32));
    }

    @Test
    void decoratedHoneyAndWaxUseLowBits() {
        assertEquals(NBeehiveColor.Kind.HONEY, NBeehiveColor.kindOf(11));
        assertEquals(NBeehiveColor.Kind.HONEY, NBeehiveColor.kindOf(27));
        assertEquals(NBeehiveColor.Kind.BOTH, NBeehiveColor.kindOf(15));
        assertEquals(NBeehiveColor.Kind.BOTH, NBeehiveColor.kindOf(31));
        assertEquals(NBeehiveColor.Kind.WAX, NBeehiveColor.kindOf(6));
    }
}
