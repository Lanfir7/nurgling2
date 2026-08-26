package nurgling.conf;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NAreaRadStyleTest {

    @Test
    void withAlphaKeepsRgbAndClamps() {
        Color src = new Color(192, 0, 0, 255);
        Color out = NAreaRadStyle.withAlpha(src, 128);
        assertEquals(192, out.getRed());
        assertEquals(0, out.getGreen());
        assertEquals(0, out.getBlue());
        assertEquals(128, out.getAlpha());

        assertEquals(0, NAreaRadStyle.withAlpha(src, -10).getAlpha());
        assertEquals(255, NAreaRadStyle.withAlpha(src, 400).getAlpha());
    }

    @Test
    void numberOrUsesDefaultAndClamp() {
        assertEquals(10, NAreaRadStyle.numberOr(null, 10, 1, 30));
        assertEquals(4, NAreaRadStyle.numberOr(4, 10, 1, 30));
        assertEquals(4, NAreaRadStyle.numberOr(4.0, 10, 1, 30));
        assertEquals(1, NAreaRadStyle.numberOr(0, 10, 1, 30));
        assertEquals(30, NAreaRadStyle.numberOr(99, 10, 1, 30));
    }

    @Test
    void styleDefaultsMatchCurrentLook() {
        assertEquals(10, NAreaRadStyle.DEF_BAND);
        assertEquals(4, NAreaRadStyle.DEF_LINE);
        assertEquals(128, NAreaRadStyle.DEF_ALPHA);
        assertEquals(150, NAreaRadStyle.DEF_BEEHIVE_RADIUS);
        assertEquals(new Color(192, 0, 0), NAreaRadStyle.DEF_ANIMAL_FILL);
        assertEquals(new Color(255, 224, 96), NAreaRadStyle.DEF_ANIMAL_EDGE);
        assertEquals(new Color(0, 163, 192), NAreaRadStyle.DEF_BEEHIVE_FILL);
        assertEquals(new Color(0, 192, 0), NAreaRadStyle.DEF_BEEHIVE_EDGE);
    }
}
