package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NGobHitBoxOverlayTest {
    @Test
    void mammothSkullAttachesOverlayWhenCustomHitBoxExists() {
        assertTrue(NGob.shouldAttachHitBoxOverlay(
                NHitBox.findCustom("gfx/kritter/mammothskull"), false));
    }

    @Test
    void skipsSecondOverlayIfAlreadyPresent() {
        assertFalse(NGob.shouldAttachHitBoxOverlay(
                NHitBox.findCustom("gfx/kritter/mammothskull"), true));
    }

    @Test
    void skipsOverlayWithoutHitBox() {
        assertFalse(NGob.shouldAttachHitBoxOverlay(null, false));
    }

    @Test
    void leftoverSkullIsStaticSoPathfindingKeepsTheBox() {
        assertFalse(NGob.isDynamicResource("gfx/kritter/mammothskull"));
        assertTrue(NGob.isDynamicResource("gfx/kritter/mammoth/mammoth"));
    }

    @Test
    void loadingOverlayWithNullSpriteIsNotAHitboxOverlay() {
        assertFalse(NGob.overlaySprMatches(null, nurgling.overlays.NModelBox.class));
    }
}
