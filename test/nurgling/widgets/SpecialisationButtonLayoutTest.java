package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SpecialisationButtonLayoutTest {
    @Test
    void keepsSubtypeButtonInsideNarrowAreaListRow() {
        assertArrayEquals(new int[] {143},
                SpecialisationButtonLayout.rightAlignedPositions(164, 4, 1, 17));
    }

    @Test
    void usesTheScrollbarAdjustedRowWidthForMultipleButtons() {
        assertArrayEquals(new int[] {108, 126},
                SpecialisationButtonLayout.rightAlignedPositions(147, 4, 1, 17, 17));
    }
}
