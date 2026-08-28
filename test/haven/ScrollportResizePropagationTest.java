package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollportResizePropagationTest {
    @Test
    void childResizeUpdatesScrollRangeAndNotifiesViewport() {
        TrackingParent parent = new TrackingParent();
        TrackingScrollcont content = parent.add(new TrackingScrollcont(new Coord(100, 50)));
        Widget child = content.add(new Widget(new Coord(80, 40)));

        child.resize(new Coord(80, 120));

        assertEquals(120, content.contentHeight);
        assertTrue(parent.reflows > 0);
    }

    private static class TrackingParent extends Widget {
        int reflows;

        @Override
        public void cresize(Widget child) {
            reflows++;
        }
    }

    private static class TrackingScrollcont extends Scrollport.Scrollcont {
        int contentHeight;

        TrackingScrollcont(Coord size) {
            super(size);
        }

        @Override
        public void update() {
            contentHeight = contentsz().y;
        }
    }
}
