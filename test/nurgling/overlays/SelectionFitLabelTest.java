package nurgling.overlays;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionFitLabelTest {
    @Test
    void captionSitsUnderTheZoneSize() {
        assertEquals("14pcs", SelectionFitLabel.caption(14));
        assertEquals("5\u00d73\n14pcs", SelectionFitLabel.text("5\u00d73", 14));
    }

    @Test
    void stackedImageIsTallerThanTheSizeLine() {
        BufferedImage size = new BufferedImage(12, 8, BufferedImage.TYPE_INT_ARGB);
        BufferedImage pieces = new BufferedImage(20, 6, BufferedImage.TYPE_INT_ARGB);
        BufferedImage stacked = SelectionFitLabel.stack(size, pieces);
        assertEquals(20, stacked.getWidth());
        assertTrue(stacked.getHeight() > size.getHeight());
        assertEquals(8 + 2 + 6, stacked.getHeight());
    }
}
