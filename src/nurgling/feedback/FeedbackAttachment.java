package nurgling.feedback;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class FeedbackAttachment {
    private final BufferedImage image;
    private final byte[] png;

    private FeedbackAttachment(BufferedImage image, byte[] png) {
        this.image = image;
        this.png = png;
    }

    public static FeedbackAttachment from(BufferedImage source) throws IOException {
        if(source == null || source.getWidth() < 1 || source.getHeight() < 1)
            throw new IOException("Invalid screenshot");
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if(!ImageIO.write(image, "png", output) || output.size() == 0)
            throw new IOException("PNG encoding is unavailable");
        return new FeedbackAttachment(image, output.toByteArray());
    }

    public BufferedImage image() {
        return image;
    }

    public byte[] png() {
        return Arrays.copyOf(png, png.length);
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }
}
