package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NDraggableLayoutTest {
    @Test
    void chromeDoesNotReserveAGutter() {
        assertEquals(Coord.z, NDraggableLayout.chrome());
        assertEquals(Coord.z, NDraggableLayout.contentOrigin());
        assertEquals(Coord.of(200, 80), NDraggableLayout.frameSize(Coord.of(200, 80)));
    }

    @Test
    void scaledSizeGrowsTheFrameWithTheVisual() {
        assertEquals(Coord.of(200, 80), NDraggableLayout.scaledSize(Coord.of(200, 80), 1.0));
        assertEquals(Coord.of(300, 120), NDraggableLayout.scaledSize(Coord.of(200, 80), 1.5));
        assertEquals(Coord.of(100, 40), NDraggableLayout.scaledSize(Coord.of(200, 80), 0.5));
    }

    @Test
    void aClickOnTheScaledPanelMapsOntoTheContent() {
        Coord natural = Coord.of(200, 80);
        Coord frame = NDraggableLayout.scaledSize(natural, 1.5);
        Coord origin = NDraggableLayout.contentOrigin();
        assertTrue(NDraggableLayout.hitsScaledContent(Coord.of(0, 0), frame, 1.5, origin, natural));
        assertTrue(NDraggableLayout.hitsScaledContent(Coord.of(299, 119), frame, 1.5, origin, natural));
        assertEquals(Coord.of(100, 40), NDraggableLayout.toContent(Coord.of(150, 60), 1.5, origin));
        assertFalse(NDraggableLayout.hitsScaledContent(Coord.of(300, 0), frame, 1.5, origin, natural));
        assertFalse(NDraggableLayout.hitsScaledContent(Coord.of(0, 120), frame, 1.5, origin, natural));
    }
}
