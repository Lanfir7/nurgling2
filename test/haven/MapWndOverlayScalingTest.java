package haven;

import haven.render.BufPipe;
import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MapWndOverlayScalingTest {
    @Test
    void overlayUsesTheCalculatedTerrainSizeInsteadOfTextureSize() {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            RecordingGOut g = new RecordingGOut();
            Tex overlay = new TexI(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB));
            Coord ul = new Coord(17, 29);
            Coord terrainSize = new Coord(237, 119);

            MapWnd.drawGridOverlay(g, overlay, ul, terrainSize);

            assertSame(overlay, g.texture);
            assertEquals(ul, g.ul);
            assertEquals(terrainSize, g.size);
        } finally {
            NConfig.current = previous;
        }
    }

    private static final class RecordingGOut extends GOut {
        private Tex texture;
        private Coord ul;
        private Coord size;

        private RecordingGOut() {
            super(null, new BufPipe(), Coord.of(1_000));
        }

        @Override
        public void image(Tex texture, Coord ul, Coord size) {
            this.texture = texture;
            this.ul = ul;
            this.size = size;
        }
    }
}
