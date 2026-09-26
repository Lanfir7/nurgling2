package nurgling.widgets.bots;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nurgling.tools.VSpec;

/**
 * Nearby loose items on the ground that PathFinder can reach in the loaded
 * view (about 41 tiles) without recorded ChunkNav data.
 */
public final class MasterMinerGroundStacks {
    /** World units: PathFinder visible area is 41 tiles, each 11 units. */
    public static final double PICKUP_RADIUS = 41 * 11;
    public static final int CLICK_PICKUP_LIMIT = 30;

    private MasterMinerGroundStacks() {
    }

    public static int pickupCap(boolean takeAll) {
        return takeAll ? Integer.MAX_VALUE : CLICK_PICKUP_LIMIT;
    }

    public static final class Drop {
        public final String resPath;
        public final double x;
        public final double y;

        public Drop(String resPath, double x, double y) {
            this.resPath = resPath;
            this.x = x;
            this.y = y;
        }
    }

    public static final class Stack {
        public final String resPath;
        public final String displayName;
        public final int count;

        public Stack(String resPath, String displayName, int count) {
            this.resPath = resPath;
            this.displayName = displayName;
            this.count = count;
        }
    }

    public static boolean isGroundItem(String resPath) {
        if (resPath == null) {
            return false;
        }
        if (!resPath.startsWith("gfx/terobjs/items/")) {
            return false;
        }
        return !resPath.contains("/decal") && !resPath.contains("parchment-decal");
    }

    /** Generic loose stone has no Chipper item-name mapping but is valid support stock. */
    public static boolean isGenericSupportStone(String resPath) {
        return isGroundItem(resPath) && "gfx/terobjs/items/stone".equals(resPath);
    }

    public static String displayName(String resPath) {
        String slug = lastSegment(resPath).replace('-', ' ').replace('_', ' ');
        if ("quarryquartz".equalsIgnoreCase(slug)) {
            return "Quarryartz";
        }
        if (slug.isEmpty()) {
            return "?";
        }
        String[] words = slug.split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                b.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return b.toString();
    }

    public static String iconInvPath(String resPath) {
        return "gfx/invobjs/" + lastSegment(resPath);
    }

    /** Resolve loose-item resource names back to the player-facing Chipper item names. */
    public static String minedItemName(String resPath) {
        String slug = lastSegment(resPath);
        for (Map.Entry<String, ArrayList<String>> entry : VSpec.object.entrySet()) {
            if (slug.equalsIgnoreCase(lastSegment(entry.getKey())) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return displayName(resPath);
    }

    public static List<Stack> group(List<Drop> drops, double px, double py, double radius) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (drops != null) {
            double r2 = radius * radius;
            for (Drop drop : drops) {
                if (drop == null || !isGroundItem(drop.resPath)) {
                    continue;
                }
                double dx = drop.x - px;
                double dy = drop.y - py;
                if ((dx * dx) + (dy * dy) >= r2) {
                    continue;
                }
                counts.merge(drop.resPath, 1, Integer::sum);
            }
        }
        List<Stack> stacks = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            stacks.add(new Stack(e.getKey(), displayName(e.getKey()), e.getValue()));
        }
        stacks.sort(Comparator
                .comparingInt((Stack s) -> -s.count)
                .thenComparing(s -> s.displayName, String.CASE_INSENSITIVE_ORDER));
        return stacks;
    }

    private static String lastSegment(String resPath) {
        if (resPath == null) {
            return "";
        }
        int slash = resPath.lastIndexOf('/');
        return slash >= 0 ? resPath.substring(slash + 1) : resPath;
    }
}
