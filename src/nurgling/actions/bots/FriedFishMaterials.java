package nurgling.actions.bots;

import haven.Coord2d;
import nurgling.tools.NAlias;
import nurgling.tools.VSpec;

import java.util.Collection;

public final class FriedFishMaterials {
    static final int POW_BLOCKED_MASK = 48;
    static final int FIRE_LIT_MASK = 5;

    private FriedFishMaterials() {}

    public static boolean fromInventory(boolean inputHasPiles) {
        return !inputHasPiles;
    }

    public static boolean toContainers(boolean outputHasContainers) {
        return outputHasContainers;
    }

    public static Coord2d closestSpot(Coord2d origin, Collection<Coord2d> candidates) {
        if (origin == null || candidates == null)
            return null;
        Coord2d best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Coord2d pos : candidates) {
            if (pos == null)
                continue;
            double d = pos.dist(origin);
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    public static boolean isUsableRoastspitPow(long modelAttr, boolean hasRoastspit) {
        return hasRoastspit && (modelAttr & POW_BLOCKED_MASK) == 0;
    }

    public static boolean isSpitReadyToWork(String content, long modelAttr) {
        return content == null || !content.contains("raw") || (modelAttr & FIRE_LIT_MASK) != FIRE_LIT_MASK;
    }

    public static boolean shouldKeepWorking(boolean fromInventory, boolean hasPiles, boolean hasInvFish, boolean spitHasContent) {
        if (spitHasContent)
            return true;
        return fromInventory ? hasInvFish : hasPiles;
    }

    public static boolean isCookedSpitroast(String name) {
        return name != null && name.toLowerCase().contains("spitroast");
    }

    public static NAlias roastableRaw() {
        NAlias raw = VSpec.getAllFish();
        raw.keys.addAll(VSpec.getCategoryContent("Clean Animal Carcass"));
        raw.keys.addAll(VSpec.getCategoryContent("Clean Bird Carcass"));
        raw.exceptions.add("Spitroast");
        raw.buildCaches();
        return raw;
    }
}
