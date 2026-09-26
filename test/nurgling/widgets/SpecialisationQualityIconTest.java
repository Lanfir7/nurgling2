package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialisationQualityIconTest {
    @Test
    void qualityLetterHasTransparentBackgroundAndOpenCounter() {
        BufferedImage icon = Specialisation.qualitySpecialisationImage();
        assertEquals(32, icon.getWidth());
        assertEquals(32, icon.getHeight());
        assertEquals(0, icon.getRGB(0, 0) >>> 24);
        int transparentCenterPixels = 0;
        int visiblePixels = 0;
        for (int y = 10; y < 22; y++) {
            for (int x = 10; x < 22; x++) {
                if ((icon.getRGB(x, y) >>> 24) == 0)
                    transparentCenterPixels++;
                else
                    visiblePixels++;
            }
        }
        assertTrue(transparentCenterPixels > 0);
        assertTrue(visiblePixels > 0);
    }
}
