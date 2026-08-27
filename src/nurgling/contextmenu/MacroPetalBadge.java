package nurgling.contextmenu;

import haven.Coord;
import haven.Tex;
import haven.TexI;
import haven.UI;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class MacroPetalBadge {
    static final int SIZE = 14;

    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int pad = Math.max(1, size / 16);
        g.setColor(new Color(40, 150, 70));
        g.fillOval(pad, pad, size - pad * 2 - 1, size - pad * 2 - 1);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, size * 9 / 16)));
        FontMetrics fm = g.getFontMetrics();
        String m = "M";
        int x = (size - fm.stringWidth(m)) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(m, x, y);
        g.dispose();
        return img;
    }

    private static Tex tex;

    static Tex tex() {
        if (tex == null)
            tex = new TexI(render(UI.scale(SIZE)));
        return tex;
    }

    private MacroPetalBadge() {}
}
