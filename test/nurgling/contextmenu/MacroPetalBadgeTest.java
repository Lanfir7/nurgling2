package nurgling.contextmenu;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroPetalBadgeTest {

    @Test
    void rendersSquareOpaqueCenter() {
        BufferedImage img = MacroPetalBadge.render(16);
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());
        int center = img.getRGB(8, 8);
        assertTrue(((center >>> 24) & 0xff) > 200, "center of the M badge should be opaque");
    }

    @Test
    void cornerStaysTransparent() {
        BufferedImage img = MacroPetalBadge.render(16);
        int corner = img.getRGB(0, 0);
        assertEquals(0, (corner >>> 24) & 0xff);
    }
}
