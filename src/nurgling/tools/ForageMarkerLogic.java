package nurgling.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ForageMarkerLogic {
    public static final double MIN_QUALITY = 40;
    public static final int DEDUP_RADIUS = 11;
    public static final long QUALITY_WAIT_MS = 2000L;
    public static final String ID_PREFIX = "forage_";

    private ForageMarkerLogic() {}

    public static boolean isPickAction(String name) {
        return "Pick".equals(name);
    }

    public static boolean isGardenPot(String gobName) {
        return gobName != null && gobName.toLowerCase(Locale.ROOT).contains("gardenpot");
    }

    public static boolean shouldPlace(Float quality) {
        return quality != null && quality >= MIN_QUALITY;
    }

    public static boolean acceptsPickedItemInventory(boolean isMainInv, boolean hasParentGob) {
        return isMainInv || !hasParentGob;
    }

    public static boolean isForageId(String locationId) {
        return locationId != null && locationId.startsWith(ID_PREFIX);
    }

    public static String formatLabel(double quality) {
        return String.format(Locale.US, "q%.0f", quality);
    }

    public static double parseQuality(String label) {
        if (label == null || !label.startsWith("q")) return 0;
        try {
            return Double.parseDouble(label.substring(1).trim().replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String forageLocationId(long segmentId, int tileX, int tileY, String resourceType) {
        String type = resourceType != null ? resourceType.replaceAll("[^a-zA-Z0-9]", "_") : "item";
        return ID_PREFIX + segmentId + "_" + tileX + "_" + tileY + "_" + type + "_" + System.currentTimeMillis();
    }

    public static boolean matchesMapSearch(String resourceType, String label, String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) return true;
        String p = pattern.toLowerCase(Locale.ROOT);
        String type = resourceType != null ? resourceType.toLowerCase(Locale.ROOT) : "";
        String lab = label != null ? label.toLowerCase(Locale.ROOT) : "";
        return type.contains(p) || lab.contains(p);
    }

    public static boolean matchesWindowSearch(String resourceType, String label, String selectedType, Double minQuality) {
        if (resourceType == null) return false;
        if (selectedType != null && !"Any".equals(selectedType) && !selectedType.equals(resourceType)) {
            return false;
        }
        if (minQuality != null) {
            return parseQuality(label) >= minQuality;
        }
        return true;
    }

    public static final class Neighbor {
        public final String locationId;
        public final double quality;
        public Neighbor(String locationId, double quality) {
            this.locationId = locationId;
            this.quality = quality;
        }
    }

    public static final class Dedup {
        public final boolean skip;
        public final List<String> removeIds;
        public Dedup(boolean skip, List<String> removeIds) {
            this.skip = skip;
            this.removeIds = removeIds;
        }
    }

    public static Dedup decideDedup(double newQuality, List<Neighbor> nearbySameType) {
        if (nearbySameType == null || nearbySameType.isEmpty()) {
            return new Dedup(false, Collections.emptyList());
        }
        List<String> remove = new ArrayList<String>();
        for (Neighbor n : nearbySameType) {
            if (n.quality >= newQuality) {
                return new Dedup(true, Collections.emptyList());
            }
            remove.add(n.locationId);
        }
        return new Dedup(false, remove);
    }
}
