package nurgling.overlays;

import haven.MessageBuf;
import haven.UI;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NIconSignOverlayTest {
    @Test
    void decodesDisplayedItemResourceFromLittleEndianSpriteData() {
        MessageBuf data = new MessageBuf(new byte[]{0x34, 0x12, 0x55});

        assertEquals(0x1234, NIconSignOverlay.contentResourceId(data));
        assertEquals(0x34, data.uint8());
    }

    @Test
    void usesLocalizedTooltipAndFallsBackToReadableResourceName() {
        assertEquals("Лисички", NIconSignOverlay.displayText("Лисички", "gfx/invobjs/chantrelle"));
        assertEquals("Fish salmon", NIconSignOverlay.displayText("", "gfx/invobjs/fish-salmon"));
    }

    @Test
    void rendersCaptionOnRoundedDarkPlateWithWarmBorder() {
        BufferedImage image = NIconSignOverlay.renderLabel("Chantrelle");

        assertEquals(0, new Color(image.getRGB(0, 0), true).getAlpha());
        Color edge = new Color(image.getRGB(UI.scale(3), image.getHeight() / 2), true);
        assertTrue(edge.getAlpha() >= 140);
        assertTrue(edge.getRed() > edge.getBlue());
        Color plate = new Color(image.getRGB(UI.scale(6), image.getHeight() / 2), true);
        assertTrue(plate.getAlpha() >= 150);
        assertTrue(plate.getRed() < 90 && plate.getGreen() < 90 && plate.getBlue() < 90);
        assertTrue(hasLightTextPixel(image));
    }

    @Test
    void attachesOnlyToIconSigns() {
        assertTrue(NIconSignOverlay.supports("gfx/terobjs/iconsign"));
        assertFalse(NIconSignOverlay.supports("gfx/terobjs/barrel"));
        assertFalse(NIconSignOverlay.supports(null));
    }

    @Test
    void deferredAttachmentRechecksCurrentStateAndCannotDuplicateOverlay() {
        List<Runnable> deferred = new ArrayList<>();
        AtomicBoolean isSign = new AtomicBoolean(true);
        AtomicBoolean attached = new AtomicBoolean();
        AtomicInteger additions = new AtomicInteger();
        Runnable attach = () -> {
            attached.set(true);
            additions.incrementAndGet();
        };

        NIconSignOverlay.scheduleAttachment(deferred::add, isSign::get, attached::get, attach);
        isSign.set(false);
        NIconSignOverlay.scheduleAttachment(deferred::add, isSign::get, attached::get, attach);
        isSign.set(true);
        NIconSignOverlay.scheduleAttachment(deferred::add, isSign::get, attached::get, attach);
        deferred.forEach(Runnable::run);

        assertEquals(1, additions.get());
    }

    private static boolean hasLightTextPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                if (pixel.getAlpha() > 200 && pixel.getRed() > 225 && pixel.getGreen() > 215)
                    return true;
            }
        }
        return false;
    }
}
