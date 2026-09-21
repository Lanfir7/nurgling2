package nurgling.overlays;

import haven.Text;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

/** Size of a dragged zone with the number of objects that fit, drawn underneath. */
public final class SelectionFitLabel {
    private static final int GAP = 2;

    private SelectionFitLabel() {}

    public static String caption(int pieces) {
        return pieces + "pcs";
    }

    public static String text(String size, int pieces) {
        return size + "\n" + caption(pieces);
    }

    public static Text of(String size, int pieces) {
        BufferedImage sizeImg = Text.render(size).img;
        BufferedImage pcsImg = Text.render(caption(pieces)).img;
        return new Label(text(size, pieces), stack(sizeImg, pcsImg));
    }

    public static BufferedImage stack(BufferedImage top, BufferedImage bottom) {
        int w = Math.max(top.getWidth(), bottom.getWidth());
        int h = top.getHeight() + GAP + bottom.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();
        g.drawImage(top, (w - top.getWidth()) / 2, 0, null);
        g.drawImage(bottom, (w - bottom.getWidth()) / 2, top.getHeight() + GAP, null);
        g.dispose();
        return img;
    }

    private static final class Label extends Text {
        private Label(String text, BufferedImage img) {
            super(text, img);
        }
    }
}
