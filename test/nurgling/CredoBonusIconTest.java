package nurgling;

import haven.Coord;
import haven.PUtils;
import haven.RichText;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredoBonusIconTest {
    @Test
    void completionIconContainsVisiblePixels() {
        BufferedImage image = CredoBonusIcon.image();
        int visible = 0;
        for(int y = 0; y < image.getHeight(); y++) {
            for(int x = 0; x < image.getWidth(); x++) {
                if((image.getRGB(x, y) >>> 24) != 0)
                    visible++;
            }
        }
        assertTrue(visible > 0);
    }

    @Test
    void completionMarkerResolvesAndSupportsRichTextScaling() {
        int[] position = {0};
        RichText.Image marker = CredoBonusIcon.source((args, pos) -> null)
            .get(new String[] {CredoBonusIcon.ID, "h=0.8ln"}, position);

        assertNotNull(marker);
        assertEquals(1, position[0]);
        assertSame(CredoBonusIcon.image(), marker.img);
        assertDoesNotThrow(() -> PUtils.uiscale(marker.img, new Coord(14, 12)));
    }

    @Test
    void completionMarkerSourcePreventsResourceFallback() {
        boolean[] fallbackUsed = {false};
        RichText.ImageSource source = CredoBonusIcon.source((args, position) -> {
            fallbackUsed[0] = true;
            return null;
        });
        int[] position = {0};

        RichText.Image marker = source.get(new String[] {CredoBonusIcon.ID, "h=0.8ln"}, position);

        assertNotNull(marker);
        assertFalse(fallbackUsed[0]);
        assertEquals(1, position[0]);
    }
}
