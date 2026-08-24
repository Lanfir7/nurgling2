package nurgling.tools;

public final class QualityPick {
    private QualityPick() {}

    public static double orZero(Float q) {
        return q == null ? 0.0 : q.doubleValue();
    }

    public static int highest(double[] quality, boolean[] equipped) {
        if (quality == null || quality.length == 0) {
            return -1;
        }
        int best = 0;
        for (int i = 1; i < quality.length; i++) {
            int cmp = Double.compare(quality[i], quality[best]);
            if (cmp > 0 || (cmp == 0 && equipped[i] && !equipped[best])) {
                best = i;
            }
        }
        return best;
    }
}
