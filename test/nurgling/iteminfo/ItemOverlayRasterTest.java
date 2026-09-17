package nurgling.iteminfo;

import haven.Tex;
import haven.Text;
import nurgling.NConfig;
import nurgling.conf.ItemQualityOverlaySettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemOverlayRasterTest {
    private static NConfig previousConfig;

    @BeforeAll
    static void fonts() {
        previousConfig = NConfig.current;
        if(NConfig.current == null)
            NConfig.current = new NConfig();
    }

    @AfterAll
    static void restoreConfig() {
        NConfig.current = previousConfig;
    }

    @Test
    void qualityTextHonorsDecimalSetting() {
        ItemQualityOverlaySettings s = new ItemQualityOverlaySettings();
        s.showDecimal = true;
        assertEquals("25.5", ItemOverlayRaster.qualityText(25.5, s));
        s.showDecimal = false;
        assertEquals("26", ItemOverlayRaster.qualityText(25.5, s));
    }

    @Test
    void rasterGrowsWithScaleInsteadOfCopyingPixels() {
        ItemQualityOverlaySettings s = new ItemQualityOverlaySettings();
        s.showBackground = false;
        s.showOutline = false;
        BufferedImage a = ItemOverlayRaster.render("25.5", Color.WHITE, s, Font.BOLD, 1.0);
        BufferedImage b = ItemOverlayRaster.render("25.5", Color.WHITE, s, Font.BOLD, 1.5);
        assertTrue(b.getWidth() > a.getWidth(), "scaled overlay glyphs must be wider, not a stretched copy");
        assertTrue(b.getHeight() > a.getHeight());
    }

    @Test
    void overlayTexIsScaleAwareAndKeepsLayoutSize() {
        ItemQualityOverlaySettings s = new ItemQualityOverlaySettings();
        s.showBackground = false;
        s.showOutline = false;
        Tex tex = ItemOverlayRaster.tex("25.5", Color.CYAN, s);
        BufferedImage layout = ItemOverlayRaster.render("25.5", Color.CYAN, s, Font.BOLD, 1.0);
        assertTrue(tex instanceof Text.ScaleRasterTex);
        assertEquals(layout.getWidth(), tex.sz().x);
        assertEquals(layout.getHeight(), tex.sz().y);
    }
}
