package nurgling.actions.bots;

import haven.Coord2d;
import haven.Gob;

public final class ButcherTarget {
    public enum Mode {
        SINGLE,
        ZONE,
        LOCAL
    }

    private ButcherTarget() {}

    public static boolean isKritter(String name) {
        return name != null && name.contains("kritter");
    }

    public static boolean isCarcassPose(String pose) {
        return pose != null && (pose.contains("knock") || pose.contains("dead"));
    }

    public static boolean isCarcass(String name, String pose) {
        return isKritter(name) && isCarcassPose(pose);
    }

    public static boolean isCarcass(Gob gob) {
        if (gob == null || gob.ngob == null) {
            return false;
        }
        return isCarcass(gob.ngob.name, gob.pose());
    }

    public static Mode resolve(boolean hasTarget, boolean zoneVisible) {
        if (hasTarget) {
            return Mode.SINGLE;
        }
        if (zoneVisible) {
            return Mode.ZONE;
        }
        return Mode.LOCAL;
    }

    /** After Skin a horse gob often respawns with a new id; retry empty menus before quitting. */
    public static final int EMPTY_MENU_RETRIES = 3;
    public static final double FOLLOW_RADIUS = 33;
    /** Stay this far from carcass center on horseback so GoTo does not sit on the gob. */
    public static final double MOUNTED_REACH = 20;
    /** Horse will not start a walk pose for shorter clicks; skip GoTo. */
    public static final double MOUNTED_MIN_MOVE = 8;

    public static boolean giveUpOnEmptyMenu(int emptyStreak) {
        return emptyStreak >= EMPTY_MENU_RETRIES;
    }

    public static boolean finishedSingle(boolean carcassStillNearby) {
        return !carcassStillNearby;
    }

    /**
     * Where to ride toward a carcass. Null means already in reach — do not GoTo,
     * or the horse hangs waiting for a walk pose that never starts.
     */
    public static Coord2d mountedApproach(Coord2d player, Coord2d gob) {
        return mountedApproach(player, gob, MOUNTED_REACH, MOUNTED_MIN_MOVE);
    }

    public static Coord2d mountedApproach(Coord2d player, Coord2d gob, double reach, double minMove) {
        if (player == null || gob == null) {
            return null;
        }
        double d = player.dist(gob);
        if (d <= reach || d == 0) {
            return null;
        }
        Coord2d stop = gob.add(player.sub(gob).mul(reach / d));
        if (player.dist(stop) < minMove) {
            return null;
        }
        return stop;
    }
}
