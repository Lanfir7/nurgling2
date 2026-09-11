package nurgling.areas;

import haven.Coord2d;
import haven.Coord;
import haven.GItem;
import haven.Pair;
import haven.WItem;
import haven.res.ui.stackinv.ItemStack;
import nurgling.NGItem;
import nurgling.conf.CropRegistry;
import nurgling.tools.NAlias;
import nurgling.tools.VSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Planting quality attribution for named client areas. */
public final class PlantQualityArea {
    private static final Pattern QUALITY_SUFFIX = Pattern.compile("\\s+\\[(\\d+)]$");
    private static final Pattern INSPECT_QUALITY = Pattern.compile("Quality:\\s*(\\d+(?:[.,]\\d+)?)");

    private PlantQualityArea() {
    }

    public static String withMaximumQuality(String name, float quality) {
        if (name == null || !Float.isFinite(quality) || quality < 0)
            return name;
        int rounded = Math.round(quality);
        Matcher suffix = QUALITY_SUFFIX.matcher(name);
        if (!suffix.find())
            return name + " [" + rounded + "]";
        try {
            if (Integer.parseInt(suffix.group(1)) >= rounded)
                return name;
        } catch (NumberFormatException ignored) {
            return name;
        }
        return name.substring(0, suffix.start()) + " [" + rounded + "]";
    }

    public static int roundedMaximum(Iterable<Float> qualities) {
        if (qualities == null)
            return -1;
        float maximum = -1;
        for (Float quality : qualities) {
            if (quality != null && Float.isFinite(quality) && quality >= 0)
                maximum = Math.max(maximum, quality);
        }
        return maximum < 0 ? -1 : Math.round(maximum);
    }

    /** Returns the highest rounded quality in an item or its stack container. */
    public static int roundedMaximum(WItem item) {
        if (item == null)
            return -1;
        Iterable<GItem> stackItems = stackItems(item.item);
        if (stackItems == null && item.parent instanceof ItemStack)
            stackItems = ((ItemStack) item.parent).wmap.keySet();
        return roundedMaximum(item.item, stackItems);
    }

    /** Supports the top-level stack shell used by normal inventory activation. */
    public static int roundedMaximum(GItem item) {
        return roundedMaximum(item, stackItems(item));
    }

    private static Iterable<GItem> stackItems(GItem item) {
        return item != null && item.contents instanceof ItemStack
                ? ((ItemStack) item.contents).wmap.keySet() : null;
    }

    public static int roundedMaximum(GItem item, Iterable<GItem> stackItems) {
        List<Float> qualities = new ArrayList<>();
        if (stackItems != null) {
            for (GItem stackItem : stackItems)
                addQuality(qualities, stackItem);
        } else {
            addQuality(qualities, item);
        }
        return roundedMaximum(qualities);
    }

    private static void addQuality(List<Float> qualities, GItem item) {
        if (item instanceof NGItem)
            qualities.add(((NGItem) item).quality);
    }

    /** Crop products and every tree/bush planting material are valid planting inputs. */
    public static boolean isPlantingMaterial(WItem item) {
        if (item == null || !(item.item instanceof NGItem))
            return false;
        return isPlantingMaterial(((NGItem) item.item).name());
    }

    public static int plantingQuality(WItem item) {
        return isPlantingMaterial(item) ? roundedMaximum(item) : -1;
    }

    public static int pendingOrHeldQuality(int pendingQuality, int heldQuality) {
        return pendingQuality >= 0 ? pendingQuality : heldQuality;
    }

    public static boolean isPlantingMaterial(String itemName) {
        if (itemName == null)
            return false;
        for (List<CropRegistry.CropStage> stages : CropRegistry.HARVESTABLE.values()) {
            for (CropRegistry.CropStage stage : stages) {
                if (stage.result.matchesExact(itemName))
                    return true;
            }
        }
        return VSpec.getAllPlantableSeeds().matchesExact(itemName);
    }

    public static boolean isPlantGobResource(String resourceName) {
        if (resourceName == null)
            return false;
        if (resourceName.startsWith("gfx/terobjs/trees/") || resourceName.startsWith("gfx/terobjs/bushes/"))
            return true;
        for (NAlias crop : CropRegistry.HARVESTABLE.keySet()) {
            if (crop.matches(resourceName))
                return true;
        }
        return false;
    }

    /** Every area whose world rectangle touches the point or selection rectangle. */
    public static List<NArea> intersectedAreas(Iterable<NArea> areas, Coord2d first, Coord2d second) {
        if (areas == null || first == null || second == null)
            return Collections.emptyList();
        double minX = Math.min(first.x, second.x);
        double minY = Math.min(first.y, second.y);
        double maxX = Math.max(first.x, second.x);
        double maxY = Math.max(first.y, second.y);
        List<NArea> result = new ArrayList<>();
        for (NArea area : areas) {
            if (area == null)
                continue;
            Pair<Coord2d, Coord2d> bounds = area.getRCArea();
            if (bounds == null || bounds.a == null || bounds.b == null)
                continue;
            if (bounds.a.x <= maxX && bounds.b.x >= minX && bounds.a.y <= maxY && bounds.b.y >= minY)
                result.add(area);
        }
        return result;
    }

    /** Selected tiles are attributed by center, never by a rectangle's shared edge. */
    public static List<NArea> selectedTileAreas(Iterable<NArea> areas, Coord first, Coord second, Coord2d tileSize) {
        if (areas == null || first == null || second == null || tileSize == null || tileSize.x <= 0 || tileSize.y <= 0)
            return Collections.emptyList();
        List<NArea> source = new ArrayList<>();
        for (NArea area : areas)
            source.add(area);
        int minX = Math.min(first.x, second.x);
        int minY = Math.min(first.y, second.y);
        int maxX = Math.max(first.x, second.x);
        int maxY = Math.max(first.y, second.y);
        LinkedHashSet<NArea> result = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Coord2d center = new Coord2d((x + 0.5) * tileSize.x, (y + 0.5) * tileSize.y);
                result.addAll(intersectedAreas(source, center, center));
            }
        }
        return new ArrayList<>(result);
    }

    public static Double inspectQuality(String message) {
        if (message == null)
            return null;
        Matcher match = INSPECT_QUALITY.matcher(message);
        if (!match.find())
            return null;
        try {
            return Double.parseDouble(match.group(1).replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
