package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckBoxWrapLayoutTest {
    @Test
    void wrappedLabelUsesOnlySpaceRemainingAfterCheckbox() {
        assertEquals(145, CheckBoxWrapLayout.labelWidth(170, 20, 5));
        assertEquals(1, CheckBoxWrapLayout.labelWidth(20, 20, 5));
        assertEquals(Coord.of(170, 42), CheckBoxWrapLayout.size(
                Coord.of(20, 18), Coord.of(145, 42), 5));
    }

}
