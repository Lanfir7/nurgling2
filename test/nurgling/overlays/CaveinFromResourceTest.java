package nurgling.overlays;

import haven.FromResource;
import haven.res.gfx.fx.cavewarn.Cavein;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveinFromResourceTest {

    @Test
    void localCaveinOverridesCurrentServerResource() {
        FromResource src = Cavein.class.getAnnotation(FromResource.class);
        assertNotNull(src);
        assertEquals("gfx/fx/cavewarn", src.name());
        assertTrue(src.version() >= 9, "server ships cavewarn v9+; v8 is discarded");
        assertTrue(src.override(), "keep NMiningNumber if the server bumps cavewarn again");
    }
}
