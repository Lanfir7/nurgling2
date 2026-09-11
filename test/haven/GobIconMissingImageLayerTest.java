package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GobIconMissingImageLayerTest {
    @Test
    void imageGetDoesNotThrowWhenResourceHasNoImgc() {
        Resource res = new Resource.Virtual(null, "test/mm-missing-imgc", 1);
        GobIcon.Image img = assertDoesNotThrow(() -> GobIcon.Image.get(res));
        assertNotNull(img);
        assertNotNull(img.img);
        assertNotNull(img.tex);
        assertSame(img, GobIcon.Image.get(res));
    }

    @Test
    void imageIconFactorySurvivesMissingImgc() {
        Resource res = new Resource.Virtual(null, "test/mm-missing-imgc-icon", 1);
        OwnerContext owner = new OwnerContext() {
            public <T> T context(Class<T> cl) {
                throw new OwnerContext.NoContext(cl);
            }
        };
        GobIcon.Icon icon = assertDoesNotThrow(
                () -> GobIcon.ImageIcon.factory.create(owner, res, Message.nil));
        assertNotNull(icon.image());
        assertEquals(GobIcon.Icon.Markable.UNMARKABLE, icon.markable());
        assertEquals("???", icon.name());
    }
}
