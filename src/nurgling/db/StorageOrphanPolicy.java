package nurgling.db;

import haven.Coord;

/**
 * Pure rules for dropping ghost storage rows when the player
 * stands on a former container tile that is actually empty.
 */
public final class StorageOrphanPolicy {
    public static final int POSRES_PER_TILE = 1024;
    /** ~70% of 50-tile view; ghosts inside this radius are eligible to purge. */
    public static final int NEAR_TILES = 30;
    public static final long IDLE_MS = 3000;
    public static final long CHECK_INTERVAL_MS = 1500;

    private StorageOrphanPolicy() {}

    public static Coord parseGcoord(String stored) {
        if (stored == null) {
            return null;
        }
        String s = stored.trim();
        if (s.length() < 5 || s.charAt(0) != '(' || s.charAt(s.length() - 1) != ')') {
            return null;
        }
        String inner = s.substring(1, s.length() - 1);
        int comma = inner.indexOf(',');
        if (comma < 0) {
            return null;
        }
        try {
            int x = Integer.parseInt(inner.substring(0, comma).trim());
            int y = Integer.parseInt(inner.substring(comma + 1).trim());
            return Coord.of(x, y);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isNearby(Coord playerGcoord, Coord containerGcoord) {
        if (playerGcoord == null || containerGcoord == null) {
            return false;
        }
        int limit = NEAR_TILES * POSRES_PER_TILE;
        return Math.abs(playerGcoord.x - containerGcoord.x) <= limit
                && Math.abs(playerGcoord.y - containerGcoord.y) <= limit;
    }

    public static boolean sameTile(Coord a, Coord b) {
        if (a == null || b == null) {
            return false;
        }
        return a.div(POSRES_PER_TILE).equals(b.div(POSRES_PER_TILE));
    }

    public static boolean gobPresent(boolean hashPresent, boolean stockpileOnSameTile) {
        return hashPresent || stockpileOnSameTile;
    }

    public static boolean isGridIdle(long nowMs, long gridEnteredAtMs, long lastGobActivityAtMs) {
        if (gridEnteredAtMs <= 0) {
            return false;
        }
        long last = Math.max(gridEnteredAtMs, lastGobActivityAtMs);
        return nowMs - last >= IDLE_MS;
    }

    public static boolean shouldPurge(boolean gridLoaded, boolean idle, boolean nearby,
                                      boolean gobPresent, boolean nearbyUnhashedGob) {
        return gridLoaded && idle && nearby && !gobPresent && !nearbyUnhashedGob;
    }
}
