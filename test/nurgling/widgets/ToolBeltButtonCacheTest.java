package nurgling.widgets;

import haven.IButton;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolBeltButtonCacheTest {
    private static class Button extends IButton {
        int disposals;
        Button() {
            super(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        }
        @Override public void dispose() {
            disposals++;
            super.dispose();
        }
    }

    @Test void unchangedDrawsReuseButtonAndItsTextureOwner() {
        ToolBeltButtonCache cache = new ToolBeltButtonCache(2);
        Object scenario = new Object();
        AtomicInteger creations = new AtomicInteger();
        IButton first = null;
        for (int frame = 0; frame < 1000; frame++) {
            IButton current = cache.get(0, scenario, "scenario:Mine", "Mine", null, null, 32, 12,
                    () -> { creations.incrementAndGet(); return new Button(); });
            if (first == null) first = current;
            assertSame(first, current);
        }
        assertEquals(1, creations.get());
        cache.clear();
        assertEquals(1, ((Button) first).disposals);
    }

    @Test void renameCustomImageEditAndSourceReplacementInvalidateSameSlot() {
        ToolBeltButtonCache cache = new ToolBeltButtonCache(1);
        Object preset = new Object(), images = new Object();
        Button first = (Button) cache.get(0, preset, "equippreset:1", "Old", "icon", images, 32, 12, Button::new);
        Button renamed = (Button) cache.get(0, preset, "equippreset:1", "New", "icon", images, 32, 12, Button::new);
        assertEquals(1, first.disposals);
        Object editedImages = new Object();
        Button edited = (Button) cache.get(0, preset, "equippreset:1", "New", "icon", editedImages, 32, 12, Button::new);
        assertEquals(1, renamed.disposals);
        Button replacement = (Button) cache.get(0, new Object(), "equippreset:1", "New", "icon", editedImages, 32, 12, Button::new);
        assertEquals(1, edited.disposals);
        assertNotSame(edited, replacement);
        cache.clear();
    }

    @Test void scaleMappingAndIconChoiceInvalidateWithoutTouchingOtherSlots() {
        ToolBeltButtonCache cache = new ToolBeltButtonCache(2);
        Object source = new Object();
        Button other = (Button) cache.get(1, source, "other", "Name", null, null, 32, 12, Button::new);
        Button first = (Button) cache.get(0, source, "a", "Name", null, null, 32, 12, Button::new);
        Button mapped = (Button) cache.get(0, source, "b", "Name", null, null, 32, 12, Button::new);
        Button icon = (Button) cache.get(0, source, "b", "Name", "missing-icon", null, 32, 12, Button::new);
        Button scaled = (Button) cache.get(0, source, "b", "Name", "missing-icon", null, 48, 18, Button::new);
        assertEquals(1, first.disposals);
        assertEquals(1, mapped.disposals);
        assertEquals(1, icon.disposals);
        assertEquals(0, other.disposals);
        cache.remove(0);
        assertEquals(1, scaled.disposals);
        cache.clear();
        cache.clear();
        assertEquals(1, other.disposals);
        assertEquals(1, scaled.disposals);
    }

    @Test void failedReplacementCanRetryAndDoesNotLeakOldButton() {
        ToolBeltButtonCache cache = new ToolBeltButtonCache(1);
        Object source = new Object();
        Button first = (Button) cache.get(0, source, "a", "Old", null, null, 32, 12, Button::new);
        assertThrows(IllegalStateException.class, () -> cache.get(0, source, "a", "New", null, null, 32, 12,
                () -> { throw new IllegalStateException("resource not ready"); }));
        assertEquals(0, first.disposals);
        Button replacement = (Button) cache.get(0, source, "a", "New", null, null, 32, 12, Button::new);
        assertEquals(1, first.disposals);
        cache.clear();
        assertEquals(1, replacement.disposals);
    }
}
