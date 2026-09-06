package nurgling.overlays;

import haven.*;
import nurgling.conf.FontSettings;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Floating item-name caption for an icon sign. */
public final class NIconSignOverlay extends NObjectTexLabel {
    public static final String RESOURCE_NAME = "gfx/terobjs/iconsign";
    private static final Map<String, TexI> LABELS = new ConcurrentHashMap<>();

    private final Gob gob;
    private String shownText = "";
    private ResDrawable trackedDrawable;
    private MessageBuf trackedData;
    private Indir<Resource> itemResource;
    private Resource loadedItem;

    public NIconSignOverlay(Gob gob) {
        super(gob);
        this.gob = gob;
        this.pos = new Coord3f(0, 0, 8);
        this.forced = true;
    }

    public static boolean supports(String resourceName) {
        return RESOURCE_NAME.equals(resourceName);
    }

    static void scheduleAttachment(Consumer<Runnable> defer, BooleanSupplier isSignNow,
                                   BooleanSupplier alreadyAttached, Runnable attach) {
        defer.accept(() -> {
            if (isSignNow.getAsBoolean() && !alreadyAttached.getAsBoolean())
                attach.run();
        });
    }

    public static void ensureAttached(Gob gob) {
        scheduleAttachment(gob::defer,
                () -> {
                    Drawable drawable = gob.getattr(Drawable.class);
                    return drawable instanceof ResDrawable && drawable.getres() != null &&
                            supports(drawable.getres().name);
                },
                () -> gob.findol(NIconSignOverlay.class) != null,
                () -> gob.addol(new Gob.Overlay(gob, new NIconSignOverlay(gob)), false));
    }

    static int contentResourceId(MessageBuf data) {
        if (data == null || data.eom())
            return -1;
        MessageBuf copy = data.clone();
        if (copy.eom())
            return -1;
        int lo = copy.uint8();
        if (copy.eom())
            return -1;
        return lo | (copy.uint8() << 8);
    }

    static String displayText(String tooltip, String resourceName) {
        if (tooltip != null && !tooltip.trim().isEmpty())
            return tooltip.trim();
        if (resourceName == null || resourceName.isEmpty())
            return "";
        int slash = resourceName.lastIndexOf('/');
        String base = resourceName.substring(slash + 1).replace('-', ' ').replace('_', ' ').trim();
        if (base.isEmpty())
            return "";
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    static BufferedImage renderLabel(String value) {
        Font font = FontSettings.getOpenSansSemibold().deriveFont(Font.BOLD, (float) UI.scale(12));
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = measure.createGraphics();
        mg.setFont(font);
        FontMetrics metrics = mg.getFontMetrics();
        int textWidth = metrics.stringWidth(value);
        int textHeight = metrics.getHeight();
        int ascent = metrics.getAscent();
        mg.dispose();
        int padX = UI.scale(6);
        int padY = UI.scale(3);
        int shadow = UI.scale(2);
        int border = Math.max(1, UI.scale(1));
        int arc = UI.scale(8);
        BufferedImage out = TexI.mkbuf(new Coord(
                textWidth + padX * 2 + UI.scale(4),
                textHeight + padY * 2 + shadow + UI.scale(2)));
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int x = UI.scale(2);
        int y = UI.scale(1);
        int width = out.getWidth() - UI.scale(5);
        int height = out.getHeight() - shadow - UI.scale(2);
        g.setColor(new Color(0, 0, 0, 105));
        g.fillRoundRect(x, y + shadow, width, height, arc, arc);
        g.setColor(new Color(37, 32, 27, 218));
        g.fillRoundRect(x, y, width, height, arc, arc);
        g.setColor(new Color(196, 153, 83, 205));
        g.setStroke(new BasicStroke(border));
        g.drawRoundRect(x, y, width, height, arc, arc);
        g.setFont(font);
        g.setColor(new Color(247, 239, 218));
        g.drawString(value, x + padX, y + padY + ascent);
        g.dispose();
        return out;
    }

    @Override
    public boolean tick(double dt) {
        Drawable drawable = gob.getattr(Drawable.class);
        if (!(drawable instanceof ResDrawable) || !supports(drawable.getres().name))
            return true;

        ResDrawable sign = (ResDrawable) drawable;
        if (sign != trackedDrawable || sign.sdt != trackedData) {
            trackedDrawable = sign;
            trackedData = sign.sdt;
            itemResource = null;
            loadedItem = null;
            setText("");
            int resourceId = contentResourceId(sign.sdt);
            if (resourceId >= 0)
                itemResource = gob.context(Resource.Resolver.class).getres(resourceId);
        }

        if (itemResource != null && loadedItem == null) {
            try {
                loadedItem = itemResource.get();
                Resource.Tooltip tooltip = loadedItem.layer(Resource.tooltip);
                setText(displayText(tooltip == null ? null : tooltip.text(), loadedItem.name));
            } catch (Loading ignored) {
                // Retry after the displayed item resource finishes loading.
            }
        }
        return false;
    }

    private void setText(String value) {
        if (value.equals(shownText))
            return;
        shownText = value;
        TexI texture = value.isEmpty() ? null : LABELS.computeIfAbsent(value,
                text -> new TexI(renderLabel(text)));
        label = texture;
        img = texture;
    }
}
