package nurgling.widgets;

import haven.Coord;
import haven.Widget;
import nurgling.widgets.nsettings.Panel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPageFrameTest {
    @Test
    void contentHeightUsesLowestVisibleDescendant() {
        Widget root = new Widget(Coord.of(300, 1));
        Widget group = root.add(new Widget(Coord.of(200, 100)), Coord.of(10, 20));
        group.add(new Widget(Coord.of(40, 30)), Coord.of(0, 140));
        root.add(new Widget(Coord.of(20, 500)), Coord.z).hide();

        assertEquals(200, SettingsPageFrame.contentHeight(root, 10));
    }

    @Test
    void longStaticPageGrowsBeyondViewport() {
        Panel panel = new Panel();
        panel.resize(Coord.of(400, 700));
        SettingsPageFrame frame = new SettingsPageFrame(panel, null);

        frame.fitTo(Coord.of(500, 300), 1);

        assertTrue(frame.sz.y > 300);
    }

    @Test
    void pageGrowsWhenAsyncPanelContentArrivesAfterLayout() {
        Panel panel = new Panel();
        panel.resize(Coord.of(400, 100));
        SettingsPageFrame frame = new SettingsPageFrame(panel, null);
        frame.fitTo(Coord.of(500, 300), 1);

        panel.resize(Coord.of(400, 700));

        assertTrue(frame.sz.y > 300);
    }

    @Test
    void internalScrollOwnerStaysAtViewportHeight() {
        Panel panel = new InternalScrollPanel();
        SettingsPageFrame frame = new SettingsPageFrame(panel, null);

        frame.fitTo(Coord.of(500, 300), 1);

        assertEquals(300, frame.sz.y);
    }

    private static final class InternalScrollPanel extends Panel implements AdaptiveSettingsPanel {
        @Override
        public void fitToWidth(int width, int columns) {
            resize(Coord.of(width, 700));
        }

        @Override
        public boolean ownsVerticalScroll() {
            return true;
        }
    }
}
