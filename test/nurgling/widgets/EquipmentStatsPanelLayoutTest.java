package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentStatsPanelLayoutTest {
    @Test
    void placesStatsPanelAfterRightmostEquipmentSlot() {
        Coord panel = EquipmentStatsPanelLayout.statsPosition(
                new Coord[] {new Coord(0, 0), new Coord(190, 132)},
                new Coord(40, 40),
                20);

        assertEquals(new Coord(250, 0), panel);
    }

    @Test
    void preservesEquipmentHeightWhenExpandingTheWindow() {
        Coord expanded = EquipmentStatsPanelLayout.expandedSize(
                new Coord(230, 352), new Coord(250, 5), 220);

        assertEquals(new Coord(470, 352), expanded);
    }

    @Test
    void keepsEquipmentIndicatorsAnchoredToTheOriginalWidth() {
        Coord origin = EquipmentStatsPanelLayout.indicatorOrigin(230, 40, 85, 3);

        assertEquals(new Coord(105, 3), origin);
    }

    @Test
    void perceptionIndicatorHitAreaIncludesIconAndTextOnly() {
        Coord origin = new Coord(105, 3);

        assertTrue(EquipmentStatsPanelLayout.hitsIndicator(new Coord(106, 4), origin, 40, 20));
        assertTrue(EquipmentStatsPanelLayout.hitsIndicator(new Coord(164, 22), origin, 40, 20));
        assertFalse(EquipmentStatsPanelLayout.hitsIndicator(new Coord(165, 22), origin, 40, 20));
        assertFalse(EquipmentStatsPanelLayout.hitsIndicator(new Coord(104, 4), origin, 40, 20));
    }
}
