package nurgling.feedback;

import haven.Coord;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;

public final class SnipSelection {
    private SnipSelection() {
    }

    public static Optional<Rectangle> rectangle(Coord start, Coord end, Coord bounds, int minimumSize) {
        int left = Math.max(0, Math.min(start.x, end.x));
        int top = Math.max(0, Math.min(start.y, end.y));
        int right = Math.min(bounds.x, Math.max(start.x, end.x));
        int bottom = Math.min(bounds.y, Math.max(start.y, end.y));
        int width = Math.max(0, right - left);
        int height = Math.max(0, bottom - top);
        return (width < minimumSize || height < minimumSize)
                ? Optional.empty()
                : Optional.of(new Rectangle(left, top, width, height));
    }

    public static BufferedImage crop(BufferedImage source, Rectangle selection) {
        BufferedImage result = new BufferedImage(selection.width, selection.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, selection.width, selection.height,
                    selection.x, selection.y, selection.x + selection.width, selection.y + selection.height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }
}
