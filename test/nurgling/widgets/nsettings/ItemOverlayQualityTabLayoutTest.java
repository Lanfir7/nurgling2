package nurgling.widgets.nsettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemOverlayQualityTabLayoutTest {
    @Test
    void qualityThresholdsColumnFitsInsideTab() {
        ItemOverlayQualityTabLayout layout = ItemOverlayQualityTabLayout.forTab();

        assertEquals(560, layout.tabWidth);
        assertEquals(280, layout.leftColumnWidth);
        assertEquals(290, layout.rightColumnX);
        assertEquals(210, layout.listWidth);
        assertTrue(layout.rightColumnX + layout.listWidth <= layout.tabWidth);
        assertTrue(layout.addButtonRight(24) <= layout.tabWidth);
    }

    @Test
    void leftColumnKeepsRoomForControls() {
        ItemOverlayQualityTabLayout layout = ItemOverlayQualityTabLayout.forTab();
        int leftControlsRight = 115 + 113;
        assertTrue(leftControlsRight < layout.rightColumnX);
    }
}
