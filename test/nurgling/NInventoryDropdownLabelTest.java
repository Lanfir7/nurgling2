package nurgling;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NInventoryDropdownLabelTest {
    @Test
    void dropdownLabelsFitInsideTheirSixteenPixelContentClip() {
        int itemHeight = 16;
        int tallLabelHeight = 15;
        Coord tallLabelPosition = NInventoryDropdownLayout.labelPosition(itemHeight, tallLabelHeight);

        assertEquals(new Coord(3, 0), tallLabelPosition);
        assertTrue(tallLabelPosition.y + tallLabelHeight <= itemHeight);
    }

    @Test
    void shorterDropdownLabelsAreVerticallyCenteredWithoutLosingHorizontalPadding() {
        assertEquals(new Coord(3, 3), NInventoryDropdownLayout.labelPosition(16, 10));
    }
}
