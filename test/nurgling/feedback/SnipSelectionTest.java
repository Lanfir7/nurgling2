package nurgling.feedback;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SnipSelectionTest {
    @Test
    void normalizesReverseDragAndClampsToFrame() {
        Optional<Rectangle> result = SnipSelection.rectangle(
                Coord.of(90, 80), Coord.of(-5, 10), Coord.of(80, 60), 8);

        assertEquals(new Rectangle(0, 10, 80, 50), result.orElseThrow());
    }

    @Test
    void rejectsSelectionsSmallerThanEightPixels() {
        assertFalse(SnipSelection.rectangle(
                Coord.of(10, 10), Coord.of(17, 30), Coord.of(100, 100), 8).isPresent());
    }

    @Test
    void cropReturnsDetachedPixels() {
        BufferedImage source = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(5, 6, 0xff12ab34);

        BufferedImage crop = SnipSelection.crop(source, new Rectangle(5, 6, 8, 9));

        assertEquals(8, crop.getWidth());
        assertEquals(9, crop.getHeight());
        assertEquals(0xff12ab34, crop.getRGB(0, 0));
        crop.setRGB(0, 0, 0);
        assertEquals(0xff12ab34, source.getRGB(5, 6));
    }
}
