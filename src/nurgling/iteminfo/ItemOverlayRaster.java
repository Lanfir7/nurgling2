package nurgling.iteminfo;

import haven.Tex;
import haven.Text;
import haven.UI;
import haven.Utils;
import nurgling.NConfig;
import nurgling.conf.FontSettings;
import nurgling.conf.ItemQualityOverlaySettings;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Item-corner labels (quality, amount, volume, study time) that must stay
 * sharp when a belt or other Scale2D panel is not at 100%.
 */
public final class ItemOverlayRaster {
    private ItemOverlayRaster() {}

    public static String qualityText(double quality, ItemQualityOverlaySettings settings) {
        if(settings.showDecimal)
            return(String.format(java.util.Locale.US, "%.1f", quality));
        return(Integer.toString((int)Math.round(quality)));
    }

    public static Tex tex(String text, Color color, ItemQualityOverlaySettings settings) {
        return(tex(text, color, settings, Font.BOLD));
    }

    public static Tex tex(String text, Color color, ItemQualityOverlaySettings settings, int fontStyle) {
        BufferedImage img = render(text, color, settings, fontStyle, 1.0);
        return(Text.live(Utils.imgsz(img), img, s -> render(text, color, settings, fontStyle, s)));
    }

    public static BufferedImage render(String text, Color color, ItemQualityOverlaySettings settings, int fontStyle, double scale) {
        Font font = resolveFont(settings, fontStyle, scale);
        Text.Foundry fnd = new Text.Foundry(font, color).aa(true);
        BufferedImage img = fnd.render(text, color).img;
        if(settings.showOutline) {
            int width = Math.max(0, (int)Math.round(settings.outlineWidth * scale));
            img = outlineWithWidth(img, settings.outlineColor, width);
        }
        if(settings.showBackground)
            img = withBackground(img, settings.backgroundColor);
        return(img);
    }

    static Font resolveFont(ItemQualityOverlaySettings settings, int style, double scale) {
        float psz = Math.max(1f, Math.round(UI.scale((float)settings.fontSize) * (float)scale));
        FontSettings fontSettings = (FontSettings)NConfig.get(NConfig.Key.fonts);
        Font font = (fontSettings != null) ? fontSettings.getFont(settings.fontFamily) : null;
        if(font == null)
            return(new Font("SansSerif", style, Math.round(psz)));
        return(font.deriveFont(style, psz));
    }

    static BufferedImage outlineWithWidth(BufferedImage img, Color outlineColor, int width) {
        if(width <= 0)
            return(img);
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage result = new BufferedImage(w + width * 2, h + width * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        BufferedImage coloredImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D cg = coloredImg.createGraphics();
        cg.drawImage(img, 0, 0, null);
        cg.setComposite(AlphaComposite.SrcIn);
        cg.setColor(outlineColor);
        cg.fillRect(0, 0, w, h);
        cg.dispose();
        for(int dx = -width; dx <= width; dx++) {
            for(int dy = -width; dy <= width; dy++) {
                if(dx != 0 || dy != 0)
                    g.drawImage(coloredImg, width + dx, width + dy, null);
            }
        }
        g.drawImage(img, width, width, null);
        g.dispose();
        return(result);
    }

    static BufferedImage withBackground(BufferedImage text, Color background) {
        BufferedImage bi = new BufferedImage(text.getWidth(), text.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bi.createGraphics();
        graphics.setColor(background);
        graphics.fillRect(0, 0, bi.getWidth(), bi.getHeight());
        graphics.drawImage(text, 0, 0, null);
        graphics.dispose();
        return(bi);
    }
}
