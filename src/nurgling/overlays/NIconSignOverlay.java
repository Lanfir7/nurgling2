package nurgling.overlays;

import haven.*;
import haven.render.RenderTree;
import nurgling.conf.FontSettings;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Floating item-name caption for an icon sign. */
public final class NIconSignOverlay extends NObjectTexLabel {
    public static final String RESOURCE_NAME = "gfx/terobjs/iconsign";
    public static final String PARCHMENT_DECAL_RESOURCE = "gfx/terobjs/items/parchment-decal";
    private static final String DECAL_RESOURCE_PREFIX = "gfx/terobjs/items/decal-";

    private final Gob gob;
    private String shownText = "";
    private Object trackedData;
    private int trackedResourceId = -1;
    private boolean trackedRawParchmentPrefix;
    private Indir<Resource> itemResource;
    private Resource loadedItem;
    private int renderedFontSize = -1;
    private int renderedOpacity = -1;
    private Gob.Overlay parchmentOverlay;
    private NObjectLabelSettings settings;
    private long settingsRevision = Long.MIN_VALUE;
    private int renderSlots;

    public NIconSignOverlay(Gob gob) {
        super(gob);
        this.gob = gob;
        this.pos = new Coord3f(0, 0, 5);
        this.forced = true;
        this.parchmentOverlay = findParchmentOverlay(gob);
    }

    public static boolean supports(String resourceName) {
        return RESOURCE_NAME.equals(resourceName);
    }

    public static boolean supportsParchment(String resourceName) {
        return resourceName != null && (resourceName.startsWith(PARCHMENT_DECAL_RESOURCE) ||
                resourceName.startsWith(DECAL_RESOURCE_PREFIX));
    }

    static void scheduleAttachment(Consumer<Runnable> defer, BooleanSupplier isSupportedNow,
                                   BooleanSupplier alreadyAttached, Runnable attach) {
        defer.accept(() -> {
            if (isSupportedNow.getAsBoolean() && !alreadyAttached.getAsBoolean())
                attach.run();
        });
    }

    public static void ensureAttached(Gob gob) {
        scheduleAttachment(gob::defer,
                () -> {
                    Drawable drawable = gob.getattr(Drawable.class);
                    if (drawable instanceof ResDrawable && drawable.getres() != null &&
                            supports(drawable.getres().name))
                        return true;
                    return findParchmentData(gob) != null;
                },
                () -> gob.findol(NIconSignOverlay.class) != null,
                () -> gob.addol(new Gob.Overlay(gob, new NIconSignOverlay(gob)), false));
    }

    public static void parchmentAdded(Gob gob, Gob.Overlay source) {
        ensureAttached(gob);
        Gob.Overlay label = gob.findol(NIconSignOverlay.class);
        if (label != null && label.spr instanceof NIconSignOverlay)
            ((NIconSignOverlay) label.spr).parchmentOverlay = source;
    }

    public static void parchmentRemoved(Gob gob, Gob.Overlay source) {
        Gob.Overlay label = gob.findol(NIconSignOverlay.class);
        if (label != null && label.spr instanceof NIconSignOverlay) {
            NIconSignOverlay overlay = (NIconSignOverlay) label.spr;
            if (overlay.parchmentOverlay == source)
                overlay.parchmentOverlay = findParchmentOverlay(gob);
        }
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

    static int parchmentContentResourceId(byte[] data) {
        return parchmentContent(data).resourceId;
    }

    static ParchmentContent parchmentContent(byte[] data) {
        if (data == null || data.length < 6)
            return new ParchmentContent(-1, false);
        int base = uint16(data, 4);
        int fallback = base & 0x7fff;
        if ((base & 0x8000) == 0 || data.length < 7)
            return new ParchmentContent(fallback, false);

        int offset = 7;
        int spriteDataLength = data[6] & 0xff;
        if (spriteDataLength != 4 || offset + spriteDataLength > data.length)
            return new ParchmentContent(fallback, false);

        int firstLayer = uint16(data, offset);
        offset += spriteDataLength;
        if (offset >= data.length)
            return new ParchmentContent(fallback, false);

        int mappings = data[offset++] & 0xff;
        if (mappings > (data.length - offset) / 4)
            return new ParchmentContent(fallback, false);
        int mappedFirstLayer = -1;
        for (int i = 0; i < mappings; i++) {
            int localId = uint16(data, offset);
            int resourceId = uint16(data, offset + 2);
            if (localId == firstLayer)
                mappedFirstLayer = resourceId;
            offset += 4;
        }
        return new ParchmentContent(mappedFirstLayer >= 0 ? mappedFirstLayer : fallback,
                mappedFirstLayer >= 0);
    }

    private static int uint16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    static byte[] parchmentData(Gob.Overlay overlay, String resourceName) {
        if (overlay == null || !supportsParchment(resourceName))
            return null;
        if (overlay.sm instanceof OCache.OlSprite)
            return ((OCache.OlSprite) overlay.sm).sdt;
        if (overlay.sm instanceof Sprite.Mill.FromRes)
            return ((Sprite.Mill.FromRes) overlay.sm).sdt;
        return null;
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

    static String displayText(String tooltip, String resourceName, boolean stripRawParchmentPrefix) {
        String text = displayText(tooltip, resourceName);
        return stripRawParchmentPrefix && text.startsWith("Raw ") ? text.substring(4) : text;
    }

    static BufferedImage renderLabel(String value) {
        return renderLabel(value, 12, 50);
    }

    static BufferedImage renderLabel(String value, int fontSize, int backgroundOpacity) {
        Font font = FontSettings.getOpenSansSemibold().deriveFont(Font.BOLD, (float) UI.scale(fontSize));
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
        int alpha = Math.round(Math.max(0, Math.min(100, backgroundOpacity)) * 255f / 100f);
        g.setColor(new Color(37, 32, 27, alpha));
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
        NObjectLabelSettings settings = settings();
        this.pos = new Coord3f(0, 0, settings.height);
        Content content = findContent(settings);
        if (content == null) {
            trackedData = null;
            trackedResourceId = -1;
            trackedRawParchmentPrefix = false;
            itemResource = null;
            loadedItem = null;
            setText("", settings);
            return false;
        }

        if (content.data != trackedData || content.resourceId != trackedResourceId ||
                content.stripRawParchmentPrefix != trackedRawParchmentPrefix) {
            trackedData = content.data;
            trackedResourceId = content.resourceId;
            trackedRawParchmentPrefix = content.stripRawParchmentPrefix;
            itemResource = null;
            loadedItem = null;
            setText("", settings);
            if (content.resourceId >= 0)
                itemResource = gob.context(Resource.Resolver.class).getres(content.resourceId);
        }

        if (itemResource != null && loadedItem == null) {
            try {
                loadedItem = itemResource.get();
                Resource.Tooltip tooltip = loadedItem.layer(Resource.tooltip);
                setText(displayText(tooltip == null ? null : tooltip.text(), loadedItem.name,
                        content.stripRawParchmentPrefix), settings);
            } catch (Loading ignored) {
                // Retry after the displayed item resource finishes loading.
            }
        }
        if (renderedFontSize != settings.fontSize || renderedOpacity != settings.backgroundOpacity)
            setText(shownText, settings, true);
        return false;
    }

    private NObjectLabelSettings settings() {
        long revision = NObjectLabelSettings.revision();
        if (settings == null || settingsRevision != revision) {
            settings = NObjectLabelSettings.current();
            settingsRevision = revision;
        }
        return settings;
    }

    private Content findContent(NObjectLabelSettings settings) {
        if (!settings.enabled)
            return null;
        Drawable drawable = gob.getattr(Drawable.class);
        if (settings.iconSigns && drawable instanceof ResDrawable && drawable.getres() != null &&
                supports(drawable.getres().name)) {
            MessageBuf data = ((ResDrawable) drawable).sdt;
            return new Content(data, contentResourceId(data), false);
        }
        if (settings.parchments) {
            byte[] data = parchmentData(parchmentOverlay);
            if (data != null) {
                ParchmentContent parchment = parchmentContent(data);
                return new Content(data, parchment.resourceId, parchment.usesLayeredItemName);
            }
        }
        return null;
    }

    private static byte[] findParchmentData(Gob gob) {
        Gob.Overlay overlay = findParchmentOverlay(gob);
        return parchmentData(overlay);
    }

    private static Gob.Overlay findParchmentOverlay(Gob gob) {
        for (Gob.Overlay overlay : gob.ols) {
            if (parchmentData(overlay) != null)
                return overlay;
        }
        return null;
    }

    private static byte[] parchmentData(Gob.Overlay overlay) {
        if (overlay == null || overlay.spr == null || overlay.spr.res == null)
            return null;
        return parchmentData(overlay, overlay.spr.res.name);
    }

    void setText(String value, NObjectLabelSettings settings) {
        setText(value, settings, false);
    }

    private synchronized void setText(String value, NObjectLabelSettings settings, boolean force) {
        if (!force && value.equals(shownText) && renderedFontSize == settings.fontSize &&
                renderedOpacity == settings.backgroundOpacity && (value.isEmpty() || label != null))
            return;
        this.settings = settings;
        shownText = value;
        renderedFontSize = settings.fontSize;
        renderedOpacity = settings.backgroundOpacity;
        TexI previous = label;
        TexI texture = value.isEmpty() ? null :
                new TexI(renderLabel(value, settings.fontSize, settings.backgroundOpacity));
        label = texture;
        img = texture;
        if (previous != null)
            previous.dispose();
    }

    @Override
    public synchronized void added(RenderTree.Slot slot) {
        renderSlots++;
        if (renderSlots == 1 && label == null && !shownText.isEmpty() && settings != null)
            setText(shownText, settings, true);
    }

    @Override
    public synchronized void removed(RenderTree.Slot slot) {
        if (renderSlots > 0 && --renderSlots == 0)
            releaseTexture();
    }

    private synchronized void releaseTexture() {
        TexI texture = label;
        label = null;
        img = null;
        if (texture != null)
            texture.dispose();
    }

    @Override
    public void dispose() {
        releaseTexture();
        super.dispose();
    }

    private static final class Content {
        final Object data;
        final int resourceId;
        final boolean stripRawParchmentPrefix;

        Content(Object data, int resourceId, boolean stripRawParchmentPrefix) {
            this.data = data;
            this.resourceId = resourceId;
            this.stripRawParchmentPrefix = stripRawParchmentPrefix;
        }
    }

    static final class ParchmentContent {
        final int resourceId;
        final boolean usesLayeredItemName;

        ParchmentContent(int resourceId, boolean usesLayeredItemName) {
            this.resourceId = resourceId;
            this.usesLayeredItemName = usesLayeredItemName;
        }
    }
}
