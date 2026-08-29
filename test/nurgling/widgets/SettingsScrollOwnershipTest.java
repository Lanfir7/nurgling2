package nurgling.widgets;

import haven.Coord;
import nurgling.widgets.nsettings.Panel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsScrollOwnershipTest {
    @Test
    void internalListGetsAvailableViewportAndDoesNotGrowOuterPage() {
        InternalListPanel panel = new InternalListPanel();
        SettingsPageFrame frame = new SettingsPageFrame(panel, null);

        frame.fitTo(Coord.of(500, 300), 1);

        assertEquals(300, frame.sz.y);
        assertEquals(476, panel.lastViewport.x);
        assertTrue(panel.lastViewport.y > 0 && panel.lastViewport.y < 300);
    }

    private static final class InternalListPanel extends Panel implements AdaptiveSettingsPanel {
        private Coord lastViewport = Coord.z;

        @Override
        public void fitToWidth(int width, int columns) {
        }

        @Override
        public void fitToViewport(Coord viewport, int columns) {
            lastViewport = viewport;
            resize(viewport);
        }

        @Override
        public boolean ownsVerticalScroll() {
            return true;
        }
    }
}
