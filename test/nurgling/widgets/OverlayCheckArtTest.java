package nurgling.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class OverlayCheckArtTest {

    @Test
    void colorMeansOnUsesBrightArtForThePressedState() {
        assertArrayEquals(new String[] {"/d", "/u", "/dh", "/h"},
                NIconDock.overlayArt("/", true));
        assertArrayEquals(new String[] {"d", "u", "dh", "h"},
                NIconDock.overlayArt("", true));
    }

    @Test
    void colorMeansOffKeepsStockUpDownMapping() {
        assertArrayEquals(new String[] {"/u", "/d", "/h", "/dh"},
                NIconDock.overlayArt("/", false));
    }
}
