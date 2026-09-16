package nurgling.widgets.charsel;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCharselScreenTest {
    @Test
    void replacesOnlyTheServerCharacterSelectionContainer() {
        assertTrue(NCharselScreen.isCharsel(new Coord(800, 600)));
        assertFalse(NCharselScreen.isCharsel(new Coord(1376, 768)));
        assertFalse(NCharselScreen.isCharsel(new Coord(800, 601)));
    }
}
