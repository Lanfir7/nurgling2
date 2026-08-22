package nurgling.actions.bots;

import haven.Coord2d;
import nurgling.tools.HarvestState;

import java.util.Collection;

public final class BoughBeeMaterials {
    public static final int BOUGHS_FOR_PYRE = 4;
    public static final int BRANCHES_FOR_LIGHT = 2;
    public static final int NEAR_PYRE_TILES = 3;
    public static final int HIVE_SEARCH_TILES = 5;
    public static final int PLACE_NEAR_HIVE_TILES = 3;
    public static final String TAKE_BOUGH = "Take bough";
    public static final String TAKE_BRANCH = "Take branch";
    public static final String TREE_PICK_POSE = "gfx/borka/treepickan";
    public static final String PYRE_BUILD = "Bough Pyre";

    private BoughBeeMaterials() {}

    public static int boughsNeeded(int have) {
        return Math.max(0, BOUGHS_FOR_PYRE - have);
    }

    public static int stackPieces(Integer amount) {
        return (amount != null && amount > 0) ? amount : 1;
    }

    public static boolean hasBoughsForPyre(int have) {
        return boughsNeeded(have) == 0;
    }

    public static boolean needsBranches(int have) {
        return have < BRANCHES_FOR_LIGHT;
    }

    public static boolean shouldCollectBranchesForLight(boolean harvestTrees, int have) {
        return needsBranches(have);
    }

    public static boolean isBranchFlowerAction(String flowerOpt) {
        return TAKE_BRANCH.equals(flowerOpt);
    }

    public static boolean isNearbyPyre(double worldDist, double tileSize) {
        return worldDist <= NEAR_PYRE_TILES * tileSize;
    }

    public static boolean isHiveInRange(double worldDist, double tileSize) {
        return worldDist <= HIVE_SEARCH_TILES * tileSize;
    }

    public static boolean isWildHive(String name) {
        return name != null && name.contains("wildbees/wildbeehive");
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

    public static boolean isPyreBuild(String gobName, String builtResName) {
        if (gobName != null && gobName.contains("bpyre"))
            return true;
        return gobName != null && gobName.contains("consobj")
                && builtResName != null && builtResName.contains("bpyre");
    }

    public static boolean isPyreWindowCap(String cap) {
        if (cap == null)
            return false;
        String n = cap.toLowerCase();
        return n.contains("pyre") || n.contains("bough");
    }

    public static boolean isLivingTree(String resname) {
        if (resname == null || !resname.startsWith("gfx/terobjs/trees"))
            return false;
        return !resname.endsWith("log") && !resname.endsWith("oldtrunk") && !resname.endsWith("stump");
    }

    public static boolean isBoughTree(String gobName) {
        if (!isLivingTree(gobName))
            return false;
        int slash = gobName.lastIndexOf('/');
        String basename = slash >= 0 ? gobName.substring(slash + 1) : gobName;
        return HarvestState.hasBough(basename);
    }
}
