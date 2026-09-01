package nurgling.widgets.compass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NCompassResizeTest {
    @Test
    void rightEdgeKeepsLeftEdgeFixed() {
        NCompassResize.Result result = NCompassResize.drag(
                NCompassResize.Edge.RIGHT, 100, 600, 750, 300, 900);

        assertEquals(100, result.left);
        assertEquals(650, result.width);
    }

    @Test
    void leftEdgeKeepsRightEdgeFixed() {
        NCompassResize.Result result = NCompassResize.drag(
                NCompassResize.Edge.LEFT, 100, 600, 250, 300, 900);

        assertEquals(250, result.left);
        assertEquals(350, result.width);
    }

    @Test
    void widthIsClampedAtBothLimits() {
        NCompassResize.Result tooNarrow = NCompassResize.drag(
                NCompassResize.Edge.RIGHT, 100, 600, 200, 300, 900);
        NCompassResize.Result tooWide = NCompassResize.drag(
                NCompassResize.Edge.LEFT, 100, 600, -1000, 300, 900);

        assertEquals(100, tooNarrow.left);
        assertEquals(300, tooNarrow.width);
        assertEquals(-300, tooWide.left);
        assertEquals(900, tooWide.width);
    }
}
