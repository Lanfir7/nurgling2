package nurgling.overlays;

import haven.MessageBuf;
import haven.Gob;
import haven.OCache;
import haven.Coord2d;
import haven.TexI;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void removesRawPrefixForSecondLayerParchmentTooltip() {
        assertEquals("Beaver", NIconSignOverlay.displayText("Raw Beaver", "gfx/invobjs/beaver", true));
    }

    @Test
    void preservesRawPrefixOutsideSecondLayerParchmentTooltip() {
        assertEquals("Raw Beaver", NIconSignOverlay.displayText("Raw Beaver", "gfx/invobjs/beaver", false));
        assertEquals("Fine Raw Beaver", NIconSignOverlay.displayText("Fine Raw Beaver", "gfx/invobjs/beaver", true));
        assertEquals("raw Beaver", NIconSignOverlay.displayText("raw Beaver", "gfx/invobjs/beaver", true));
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
    void configurableStyleControlsFontSizeAndBackgroundOpacity() {
        BufferedImage small = NIconSignOverlay.renderLabel("Chantrelle", 12, 50);
        BufferedImage large = NIconSignOverlay.renderLabel("Chantrelle", 20, 50);

        Color plate = new Color(small.getRGB(UI.scale(6), UI.scale(2)), true);
        assertTrue(plate.getAlpha() >= 125 && plate.getAlpha() <= 135);
        assertTrue(large.getWidth() > small.getWidth());
        assertTrue(large.getHeight() > small.getHeight());
    }

    @Test
    void decodesItemResourceFromParchmentDataAfterItsPosition() {
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04, 0x34, 0x12, 0x55};

        assertEquals(0x1234, NIconSignOverlay.parchmentContentResourceId(data));
        assertEquals(0x1234, NIconSignOverlay.parchmentContentResourceId(
                new byte[]{0x01, 0x02, 0x03, 0x04, 0x34, (byte) 0x92, 0x00}));
        assertEquals(-1, NIconSignOverlay.parchmentContentResourceId(new byte[]{0x01, 0x02, 0x03, 0x04, 0x34}));
        assertEquals(-1, NIconSignOverlay.parchmentContentResourceId(null));
    }

    @Test
    void usesFirstMappedLayerForTwoLayerParchmentItemName() {
        byte[] data = new byte[]{
                0x01, 0x02, 0x03, 0x04,
                0x01, (byte) 0x80, 0x04,
                0x10, 0x00, 0x11, 0x00,
                0x02,
                0x10, 0x00, 0x01, 0x10,
                0x11, 0x00, 0x02, 0x10
        };

        assertEquals(0x1001, NIconSignOverlay.parchmentContentResourceId(data));
        assertTrue(NIconSignOverlay.parchmentContent(data).usesLayeredItemName);
    }

    @Test
    void preservesBaseResourceForSingleMalformedOrNonTwoLayerParchmentItems() {
        assertEquals(1, NIconSignOverlay.parchmentContentResourceId(new byte[]{
                0x01, 0x02, 0x03, 0x04,
                0x01, (byte) 0x80, 0x02,
                0x10, 0x00,
                0x01,
                0x10, 0x00, 0x01, 0x10
        }));
        assertFalse(NIconSignOverlay.parchmentContent(new byte[]{
                0x01, 0x02, 0x03, 0x04,
                0x01, (byte) 0x80, 0x04,
                0x10, 0x00, 0x11, 0x00
        }).usesLayeredItemName);
        assertEquals(1, NIconSignOverlay.parchmentContentResourceId(new byte[]{
                0x01, 0x02, 0x03, 0x04,
                0x01, (byte) 0x80, 0x04,
                0x10, 0x00, 0x11, 0x00
        }));
        assertEquals(1, NIconSignOverlay.parchmentContentResourceId(new byte[]{
                0x01, 0x02, 0x03, 0x04,
                0x01, (byte) 0x80, 0x04,
                0x10, 0x00, 0x11, 0x00,
                0x01,
                0x11, 0x00, 0x02, 0x10
        }));
        assertEquals(1, NIconSignOverlay.parchmentContentResourceId(new byte[]{
                0x01, 0x02, 0x03, 0x04,
                0x01, (byte) 0x80, 0x06,
                0x10, 0x00, 0x11, 0x00, 0x12, 0x00
        }));
    }

    @Test
    void recognizesAttachedParchmentDecals() {
        assertTrue(NIconSignOverlay.supportsParchment("gfx/terobjs/items/parchment-decal"));
        assertTrue(NIconSignOverlay.supportsParchment("gfx/terobjs/items/parchment-decal-large"));
        assertTrue(NIconSignOverlay.supportsParchment("gfx/terobjs/items/decal-hide"));
        assertFalse(NIconSignOverlay.supportsParchment("gfx/invobjs/parchment-decal"));
        assertFalse(NIconSignOverlay.supportsParchment(null));
    }

    @Test
    void extractsParchmentDataFromRealServerOverlayMill() {
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04, 0x34, 0x12};
        Gob.Overlay overlay = new Gob.Overlay(null, 7, new OCache.OlSprite(() -> null, data));

        assertSame(data, NIconSignOverlay.parchmentData(overlay,
                NIconSignOverlay.PARCHMENT_DECAL_RESOURCE));
        assertNull(NIconSignOverlay.parchmentData(overlay, "gfx/terobjs/items/board"));
    }

    @Test
    void releasesOwnedTextureWhenItsLastRenderSlotIsRemoved() {
        NIconSignOverlay overlay = new NIconSignOverlay(new Gob(null, Coord2d.z, 99));
        overlay.setText("Asp", NObjectLabelSettings.current());
        TexI texture = overlay.label;

        overlay.added(null);
        overlay.added(null);
        overlay.removed(null);

        assertSame(texture, overlay.label);
        assertSame(texture, overlay.img);

        overlay.removed(null);

        assertNull(overlay.label);
        assertNull(overlay.img);

        overlay.added(null);

        assertNotNull(overlay.label);
        assertSame(overlay.label, overlay.img);
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
