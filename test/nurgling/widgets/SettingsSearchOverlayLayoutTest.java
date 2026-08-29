package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsSearchOverlayLayoutTest {
    @Test
    void dropdownTracksSearchAndStaysInsideWindow() {
        SettingsSearchOverlayLayout layout = SettingsSearchOverlayLayout.calculate(
                Coord.of(590, 1), Coord.of(250, 18),
                Coord.of(820, 600), Coord.of(250, 240), 2);

        assertEquals(Coord.of(570, 21), layout.position);
        assertEquals(Coord.of(250, 240), layout.size);
    }

    @Test
    void dropdownClampsAboveBottomEdge() {
        SettingsSearchOverlayLayout layout = SettingsSearchOverlayLayout.calculate(
                Coord.of(200, 480), Coord.of(200, 18),
                Coord.of(500, 600), Coord.of(200, 180), 2);

        assertEquals(Coord.of(200, 420), layout.position);
    }
}
