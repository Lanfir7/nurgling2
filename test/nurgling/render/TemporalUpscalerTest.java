package nurgling.render;

import haven.Coord;
import haven.render.Texture2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemporalUpscalerTest {
    @Test void viewsKeepIndependentScratchTargetsAcrossResizeAndDispose() {
        Temporal.Upscaler a = new Temporal.Upscaler();
        Temporal.Upscaler b = new Temporal.Upscaler();
        try {
            Coord small = Coord.of(800, 600), large = Coord.of(1200, 900);
            Texture2D.Sampler2D firstA = a.buffer(small);
            Texture2D.Sampler2D firstB = b.buffer(small);
            assertNotSame(firstA, firstB);
            assertSame(firstA, a.buffer(small));
            assertSame(firstB, b.buffer(small));

            assertNotSame(firstA, a.buffer(large));
            assertSame(firstB, b.buffer(small));

            a.dispose();
            assertNotSame(firstA, a.buffer(small));
            assertSame(firstB, b.buffer(small));
        } finally {
            a.dispose();
            b.dispose();
        }
    }
}
