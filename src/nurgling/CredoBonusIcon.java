package nurgling;

import haven.Coord;
import haven.RichText;
import haven.TexI;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class CredoBonusIcon {
    static final String ID = "credodone";
    static final String MARKUP = "$img[" + ID + ",h=0.8ln]";
    private static final BufferedImage IMAGE = createImage();

    private CredoBonusIcon() {
    }

    static RichText.ImageSource source(RichText.ImageSource fallback) {
        return RichText.ImageSource.chain(
            RichText.ImageSource.id(ID, () -> new RichText.Image(IMAGE)),
            fallback);
    }

    static BufferedImage image() {
        return IMAGE;
    }

    private static BufferedImage createImage() {
        BufferedImage image = TexI.mkbuf(new Coord(12, 10));
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(96, 232, 96));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(1, 5, 4, 8);
            g.drawLine(4, 8, 11, 1);
        } finally {
            g.dispose();
        }
        return image;
    }
}
