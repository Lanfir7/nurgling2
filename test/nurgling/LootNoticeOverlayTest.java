package nurgling;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootNoticeOverlayTest {
    @Test
    void repeatedGainMergesWithoutRestartingEntranceAnimation() {
        LootNoticeOverlay overlay = new LootNoticeOverlay();
        try {
            overlay.add("ore", "Ore", null, 1);
            overlay.tick(0.5);
            overlay.add("ore", "Ore", null, 3);
            assertEquals(1, overlay.visibleCount());
            assertEquals(4, overlay.amountFor("ore"));
            assertTrue(overlay.entranceAgeFor("ore") >= 0.5);
            overlay.tick(3.0);
            assertEquals(1, overlay.visibleCount());
            overlay.tick(0.7);
            assertEquals(0, overlay.visibleCount());
        } finally {
            overlay.dispose();
        }
    }

    @Test
    void visibleRowsAreBoundedAndPanelHasTranslucentFrame() {
        LootNoticeOverlay overlay = new LootNoticeOverlay();
        try {
            for (int i = 0; i < 7; i++)
                overlay.add("item" + i, "Item " + i, null, 1);
            assertEquals(4, overlay.visibleCount());
            assertEquals(0, overlay.amountFor("item0"));
            assertEquals(1, overlay.amountFor("item6"));
            BufferedImage image = LootNoticeOverlay.panelImage(300, 57);
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            assertTrue((image.getRGB(150, 28) >>> 24) > 180);
        } finally {
            overlay.dispose();
        }
    }
}
