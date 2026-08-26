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
        assertEquals(NBeehiveColor.Kind.EMPTY, NBeehiveColor.kindOf(3));
    }
}
