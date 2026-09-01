package nurgling.widgets.compass;

import java.util.Locale;

public final class NCompassPresentation {
    private static final String[] DIRECTION_KEYS = {
            "compass.direction.e",
            "compass.direction.se",
            "compass.direction.s",
            "compass.direction.sw",
            "compass.direction.w",
            "compass.direction.nw",
            "compass.direction.n",
            "compass.direction.ne"
    };

    private NCompassPresentation() {
    }

    public static String targetLabel(String name, double distance, int extra) {
        String suffix = extra > 0 ? " +" + extra : "";
        return String.format(Locale.ROOT, "%s · %.1f m%s", name, distance, suffix);
    }

    public static String directionKey(double bearing) {
        double step = Math.PI / 4.0;
        int index = (int) Math.floor((NCompassMath.normalize(bearing) + (step / 2.0)) / step);
        return DIRECTION_KEYS[Math.floorMod(index, DIRECTION_KEYS.length)];
    }

    public static boolean isPrimaryDirection(double bearing) {
        double quarter = Math.PI / 2.0;
        double normalized = NCompassMath.normalize(bearing);
        double nearest = Math.rint(normalized / quarter) * quarter;
        return Math.abs(NCompassMath.normalize(normalized - nearest)) < 0.000001;
    }
}
