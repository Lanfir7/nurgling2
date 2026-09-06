package nurgling.db;

import haven.Coord;
import nurgling.NUtils;
import nurgling.areas.NContext;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Labels for the storage items table: tile distance and container title.
 */
public final class StorageTableInfo {
    public static final int UNKNOWN_DIST = -1;

    private StorageTableInfo() {}

    public static int tilesBetween(Coord playerGcoord, Coord containerGcoord) {
        if (playerGcoord == null || containerGcoord == null) {
            return UNKNOWN_DIST;
        }
        double tiles = Math.hypot(playerGcoord.x - containerGcoord.x, playerGcoord.y - containerGcoord.y)
                / StorageOrphanPolicy.POSRES_PER_TILE;
        return (int) Math.round(tiles);
    }

    public static String distanceLabel(int tiles) {
        return tiles < 0 ? "—" : String.valueOf(tiles);
    }

    public static boolean isUnavailable(int distanceTiles, String storageName) {
        return distanceTiles == UNKNOWN_DIST && "—".equals(storageName);
    }

    public static String containerTitle(String resName) {
        if (resName == null || resName.isEmpty()) {
            return "—";
        }
        String cap = NContext.contcaps.get(resName);
        if (cap != null) {
            return cap;
        }
        Object pretty = NUtils.prettyResName(resName);
        return pretty != null ? pretty.toString() : "—";
    }

    public static String storageLabel(String nearestName, Collection<String> allNames) {
        Set<String> unique = new LinkedHashSet<>();
        if (allNames != null) {
            for (String name : allNames) {
                if (name != null && !name.isEmpty() && !"—".equals(name)) {
                    unique.add(name);
                }
            }
        }
        if (unique.isEmpty()) {
            return "—";
        }
        String primary = (nearestName != null && unique.contains(nearestName))
                ? nearestName
                : unique.iterator().next();
        if (unique.size() == 1) {
            return primary;
        }
        return primary + " +" + (unique.size() - 1);
    }
}
