package nurgling.widgets;

import haven.*;
import nurgling.conf.ProspectKind;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a labeled icon mark on the minimap.
 * Used by Checker bots (Water, Soil) to display resource quality on the map.
 * Shows an icon with a label underneath (e.g., "q20" for quality 20).
 *
 * Prospecting sample icons are shared per resource type so a world with thousands
 * of samples still only holds a handful of images. Animal / unique marks keep a
 * per-mark image for lazy load and species-specific icons.
 */
public class LabeledMinimapMark {
    private final String locationId;
    public final String label;
    public final String resourceType;
    public final double quality;
    public final ProspectKind kind;
    public final long segmentId;
    public final Coord tileCoords;
    public final long gridId;
    public final Coord localTileCoords;
    public volatile BufferedImage iconImage;
    public final long timestamp;
    public final Color labelColor;
    /** Для маркеров животных: когда убито (мс), кем — для тултипа "убита X дней назад", "Убийца: N" */
    public final Long killedAtMs;
    public final String killedBy;
    /** Путь иконки для ленивой загрузки после перезахода (gfx/invobjs/kritter/...). */
    public final String iconPath;
    /** Тип животного для fallback-загрузки иконки (gfx/kritter/...). */
    public final String animalType;

    private volatile String iconBase64;
    private volatile TexI iconTex;

    private static final Text.Furnace labelFurnace = new PUtils.BlurFurn(
        new Text.Foundry(Text.sans, 10, Color.WHITE).aa(true),
        2, 1, new Color(60, 30, 30)
    );
    private static final Text.Furnace quarryartzFurnace = new PUtils.BlurFurn(
        new Text.Foundry(Text.sans, 8, Color.WHITE).aa(true),
        2, 1, new Color(60, 30, 30)
    );

    /* Shared caches. Marks are built on bot and loader threads and read on the
     * render thread, hence the concurrent maps. Nothing is evicted: there are only
     * a few resource types and a bounded set of quality labels. */
    private static final Map<String, Icon> icons = new ConcurrentHashMap<>();
    private static final Map<String, Text> labelTex = new ConcurrentHashMap<>();
    private static final Map<String, Text.Furnace> furnaces = new ConcurrentHashMap<>();

    /** One image plus its lazily uploaded texture, shared by every mark of a resource type. */
    private static class Icon {
        final BufferedImage img;
        private TexI tex;
        private String encoded;

        Icon(BufferedImage img) {
            this.img = img;
        }

        synchronized TexI tex() {
            if(tex == null && img != null)
                tex = new TexI(img);
            return tex;
        }

        /** PNG-encoded once and reused, so saving never re-encodes an unchanged icon. */
        synchronized String encoded() {
            if(encoded == null)
                encoded = encodeIcon(img);
            return encoded;
        }
    }

    /**
     * Register the icon used by every mark of a resource type. The first
     * registration wins, so repeated samples of the same resource reuse one image.
     */
    public static void registerIcon(String resourceType, BufferedImage img) {
        if(resourceType == null || img == null)
            return;
        icons.putIfAbsent(resourceType, new Icon(img));
    }

    /** The shared icon image for a resource type, or null if none was registered. */
    public static BufferedImage icon(String resourceType) {
        Icon icon = (resourceType == null) ? null : icons.get(resourceType);
        return (icon == null) ? null : icon.img;
    }

    /** The shared icon for a resource type as a base64 PNG, encoded once and cached. */
    public static String iconBase64(String resourceType) {
        Icon icon = (resourceType == null) ? null : icons.get(resourceType);
        return (icon == null) ? null : icon.encoded();
    }

    /** Resource types that currently have an icon, for persisting the shared icon table. */
    public static Set<String> knownIconTypes() {
        return icons.keySet();
    }

    /**
     * Create a labeled minimap mark from a prospected sample.
     */
    public LabeledMinimapMark(String label, String resourceType, double quality, long segmentId, Coord tileCoords,
                              BufferedImage iconImage, Color labelColor) {
        this(generateLocationId(segmentId, tileCoords, label), label, resourceType, quality, segmentId, tileCoords,
            -1, null, iconImage, labelColor, null, null, null, null, true);
    }

    public LabeledMinimapMark(String label, String resourceType, double quality, long segmentId, Coord tileCoords,
                              BufferedImage iconImage) {
        this(label, resourceType, quality, segmentId, tileCoords, iconImage, null);
    }

    public LabeledMinimapMark(String label, String resourceType, long segmentId, Coord tileCoords,
                              long gridId, Coord localTileCoords,
                              BufferedImage iconImage, Color labelColor) {
        this(generateLocationId(segmentId, tileCoords, label), label, resourceType, parseLabelQuality(label),
            segmentId, tileCoords, gridId, localTileCoords, iconImage, labelColor, null, null, null, null, true);
    }

    public LabeledMinimapMark(String label, String resourceType, long segmentId, Coord tileCoords,
                              BufferedImage iconImage, Color labelColor) {
        this(label, resourceType, segmentId, tileCoords, -1, null, iconImage, labelColor);
    }

    public LabeledMinimapMark(String locationId, String label, String resourceType, long segmentId,
                              Coord tileCoords, long gridId, Coord localTileCoords,
                              BufferedImage iconImage, Color labelColor) {
        this(locationId, label, resourceType, parseLabelQuality(label), segmentId, tileCoords, gridId, localTileCoords,
            iconImage, labelColor, null, null, null, null, true);
    }

    public LabeledMinimapMark(String locationId, String label, String resourceType, long segmentId,
                              Coord tileCoords, long gridId, Coord localTileCoords,
                              BufferedImage iconImage, Color labelColor, Long killedAtMs, String killedBy) {
        this(locationId, label, resourceType, parseLabelQuality(label), segmentId, tileCoords, gridId, localTileCoords,
            iconImage, labelColor, killedAtMs, killedBy, null, null, false);
    }

    public LabeledMinimapMark(String locationId, String label, String resourceType, long segmentId,
                              Coord tileCoords, long gridId, Coord localTileCoords,
                              BufferedImage iconImage, Color labelColor, Long killedAtMs, String killedBy,
                              String iconPath, String animalType) {
        this(locationId, label, resourceType, parseLabelQuality(label), segmentId, tileCoords, gridId, localTileCoords,
            iconImage, labelColor, killedAtMs, killedBy, iconPath, animalType, false);
    }

    public LabeledMinimapMark(String locationId, String label, String resourceType, long segmentId,
                              Coord tileCoords, BufferedImage iconImage, Color labelColor) {
        this(locationId, label, resourceType, segmentId, tileCoords, -1, null, iconImage, labelColor);
    }

    public LabeledMinimapMark(String label, String resourceType, long segmentId, Coord tileCoords,
                              BufferedImage iconImage) {
        this(label, resourceType, segmentId, tileCoords, iconImage, null);
    }

    private LabeledMinimapMark(String locationId, String label, String resourceType, double quality,
                               long segmentId, Coord tileCoords, long gridId, Coord localTileCoords,
                               BufferedImage iconImage, Color labelColor, Long killedAtMs, String killedBy,
                               String iconPath, String animalType, boolean shareIcon) {
        this.locationId = locationId;
        this.label = label;
        this.resourceType = resourceType != null ? resourceType : "Unknown";
        this.quality = quality;
        this.kind = ProspectKind.of(this.resourceType);
        this.segmentId = segmentId;
        this.tileCoords = tileCoords;
        this.gridId = gridId;
        this.localTileCoords = localTileCoords;
        this.iconImage = iconImage;
        this.labelColor = labelColor != null ? labelColor : Color.WHITE;
        this.timestamp = System.currentTimeMillis();
        this.killedAtMs = killedAtMs;
        this.killedBy = killedBy != null && !killedBy.isEmpty() ? killedBy : null;
        this.iconPath = iconPath != null && !iconPath.isEmpty() ? iconPath : null;
        this.animalType = animalType != null && animalType.startsWith("gfx/kritter/") ? animalType : null;
        if (iconImage != null) this.iconTex = new TexI(iconImage);
        if (shareIcon)
            registerIcon(this.resourceType, iconImage);
    }

    /**
     * Create from JSON (for loading from file).
     */
    public LabeledMinimapMark(JSONObject json) {
        this.locationId = json.getString("locationId");
        this.label = json.getString("label");
        this.resourceType = json.optString("resourceType", "Unknown");
        this.quality = json.has("quality") ? json.getDouble("quality") : parseLabelQuality(this.label);
        this.kind = ProspectKind.of(this.resourceType);
        this.segmentId = json.getLong("segmentId");
        this.tileCoords = new Coord(json.getInt("tileX"), json.getInt("tileY"));
        this.timestamp = json.getLong("timestamp");

        this.gridId = json.optLong("gridId", -1);
        if (json.has("localTileX") && json.has("localTileY")) {
            this.localTileCoords = new Coord(json.getInt("localTileX"), json.getInt("localTileY"));
        } else {
            this.localTileCoords = null;
        }

        if (json.has("labelColor")) {
            this.labelColor = new Color(json.getInt("labelColor"));
        } else {
            this.labelColor = Color.WHITE;
        }

        this.iconBase64 = json.has("iconBase64") ? json.getString("iconBase64") : null;
        this.iconImage = null;
        this.killedAtMs = json.has("killedAtMs") && !json.isNull("killedAtMs") ? json.getLong("killedAtMs") : null;
        this.killedBy = json.optString("killedBy", null);
        this.iconPath = json.optString("iconPath", null);
        String at = json.optString("animalType", null);
        this.animalType = at != null && at.startsWith("gfx/kritter/") ? at : null;
        this.iconTex = null;

        /* Legacy files stored one base64 PNG per mark. Decode it only until the
         * resource type has an icon; every later mark of that type then costs nothing. */
        if(!icons.containsKey(this.resourceType) && this.iconBase64 != null)
            registerIcon(this.resourceType, decodeIcon(this.iconBase64));
    }

    /** Decode a base64 PNG, or null if it is unusable. */
    public static BufferedImage decodeIcon(String base64) {
        if(base64 == null || base64.isEmpty())
            return null;
        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        } catch(RuntimeException | java.io.IOException e) {
            System.err.println("Failed to load icon from base64: " + e.getMessage());
            return null;
        }
    }

    /** Encode an icon as a base64 PNG, or null if it cannot be written. */
    public static String encodeIcon(BufferedImage img) {
        if(img == null)
            return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch(RuntimeException | java.io.IOException e) {
            System.err.println("Failed to save icon to base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert to JSON (for saving to file). The icon is not written here; it is
     * stored once per resource type in the file's shared icon table.
     */
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("locationId", locationId);
        json.put("label", label);
        json.put("resourceType", resourceType);
        json.put("quality", quality);
        json.put("segmentId", segmentId);
        json.put("tileX", tileCoords.x);
        json.put("tileY", tileCoords.y);
        json.put("timestamp", timestamp);
        json.put("labelColor", labelColor.getRGB());
        if (gridId != -1) {
            json.put("gridId", gridId);
        }
        if (localTileCoords != null) {
            json.put("localTileX", localTileCoords.x);
            json.put("localTileY", localTileCoords.y);
        }
        return json;
    }

    /**
     * Same overlay, new map-file place. Grid identity is unchanged; only the segment tile
     * (what the minimap draws with) follows {@code MapFile.merge}.
     */
    public LabeledMinimapMark relocated(long newSegmentId, Coord tileShift) {
        Coord nt = tileCoords.sub(tileShift);
        String newId = locationId.startsWith("animal_")
                ? locationId
                : generateLocationId(newSegmentId, nt, label);
        return new LabeledMinimapMark(newId, label, resourceType, quality, newSegmentId, nt,
                gridId, localTileCoords, iconImage, labelColor, killedAtMs, killedBy, iconPath, animalType, false);
    }

    /**
     * Recover a quality from a legacy label such as "q40". Returns 0 when the label
     * carries no number, which makes the mark visible at any threshold of 0.
     */
    private static double parseLabelQuality(String label) {
        if(label == null)
            return 0;
        StringBuilder digits = new StringBuilder();
        for(int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if((c >= '0' && c <= '9') || (c == '.' && digits.indexOf(".") < 0))
                digits.append(c);
            else if(digits.length() > 0)
                break;
        }
        if(digits.length() == 0)
            return 0;
        try {
            return Double.parseDouble(digits.toString());
        } catch(NumberFormatException e) {
            return 0;
        }
    }

    private static String generateLocationId(long segmentId, Coord tileCoords, String label) {
        return String.format("labeled_%d_%d_%d_%s", segmentId, tileCoords.x, tileCoords.y,
                           label.replaceAll("[^a-zA-Z0-9]", "_"));
    }

    /**
     * Get the icon texture for rendering. Unique (animal) marks use a per-mark
     * image; prospecting samples share one texture per resource type.
     */
    public TexI getIconTex() {
        TexI tex = iconTex;
        if (tex != null) return tex;
        if (iconImage != null) {
            tex = new TexI(iconImage);
            this.iconTex = tex;
            return tex;
        }
        String b64 = iconBase64;
        if (b64 != null) {
            BufferedImage img = decodeIcon(b64);
            if (img != null) {
                this.iconImage = img;
                tex = new TexI(img);
                this.iconTex = tex;
            }
            this.iconBase64 = null;
            if (tex != null) return tex;
        }
        Icon icon = icons.get(resourceType);
        return (icon == null) ? null : icon.tex();
    }

    /**
     * Get the label text for rendering. Renders are shared by (colour, size, text), so the
     * same "q40" in the same colour costs one texture no matter how many marks use it.
     */
    public Text getLabelText() {
        if (label == null || label.isEmpty())
            return null;
        int rgb = labelColor.getRGB();
        int fontSize = "Quarryartz".equals(resourceType) ? 8 : 10;
        return labelTex.computeIfAbsent(rgb + " " + fontSize + " " + label, k -> furnace(rgb, fontSize).render(label));
    }

    private static Text.Furnace furnace(int rgb, int fontSize) {
        if(rgb == Color.WHITE.getRGB())
            return fontSize == 8 ? quarryartzFurnace : labelFurnace;
        return furnaces.computeIfAbsent(rgb + ":" + fontSize, c -> new PUtils.BlurFurn(
            new Text.Foundry(Text.sans, fontSize, new Color(rgb)).aa(true),
            2, 1, new Color(60, 30, 30)));
    }

    public boolean isInSegment(long segId) {
        return this.segmentId == segId;
    }

    public String getLocationId() {
        return locationId;
    }

    public boolean isSameLocation(LabeledMinimapMark other) {
        return this.segmentId == other.segmentId &&
               this.tileCoords.equals(other.tileCoords);
    }

    public boolean isNear(long segId, Coord tc, int radiusTiles) {
        if (this.segmentId != segId) return false;
        int dx = Math.abs(this.tileCoords.x - tc.x);
        int dy = Math.abs(this.tileCoords.y - tc.y);
        return dx <= radiusTiles && dy <= radiusTiles;
    }
}
