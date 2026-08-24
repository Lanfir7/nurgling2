package nurgling.actions.bots;

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
}
