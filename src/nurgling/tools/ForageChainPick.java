package nurgling.tools;

import haven.MCache;

import java.util.Locale;

/** Client-side SHIFT+Pick chain for clustered forage gobs (cattails, mussel leftovers, etc.). */
public final class ForageChainPick {
    public static final String HERB_PREFIX = "gfx/terobjs/herbs/";
    /** Three tiles: nearby clump, not a map-wide sweep. */
    public static final double RADIUS = 3 * MCache.tilesz.x;

    private ForageChainPick() {}

    public static boolean isHerbGob(String gobName) {
        return gobName != null && gobName.startsWith(HERB_PREFIX);
    }

    public static boolean isChainableAction(String actionName) {
        if (ForageMarkerLogic.isPickAction(actionName)) return true;
        if (actionName == null) return false;
        String lower = actionName.toLowerCase(Locale.ROOT);
        return lower.startsWith("pick ") && !lower.startsWith("pick up");
    }

    public static boolean shouldStart(boolean shiftHeld, boolean botRunning, String actionName, String gobName) {
        return shiftHeld && !botRunning && isHerbGob(gobName) && isChainableAction(actionName);
    }

    public static Candidate selectNext(Iterable<Candidate> candidates, String gobName, long excludeId,
                                       double fromX, double fromY, double radius) {
        if (candidates == null || gobName == null) return null;
        Candidate best = null;
        double bestDist = radius;
        for (Candidate c : candidates) {
            if (c == null || c.id == excludeId || !gobName.equals(c.name)) continue;
            double d = c.dist(fromX, fromY);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    public static boolean shouldStop(boolean inventoryFull, Candidate next) {
        return inventoryFull || next == null;
    }

    public static final class Candidate {
        public final long id;
        public final String name;
        public final double x;
        public final double y;

        public Candidate(long id, String name, double x, double y) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
        }

        public double dist(double ox, double oy) {
            double dx = x - ox;
            double dy = y - oy;
            return Math.hypot(dx, dy);
        }
    }
}
