package nurgling.tools;

public final class TanningRemaining {
    public static final int TOTAL_HOURS = 30;

    private TanningRemaining() {}

    public static boolean isTubWindow(String cap) {
        return "Tub".equals(cap) || "Tanning Tub".equals(cap);
    }

    public static int remainingMinutes(double done) {
        double leftover = Math.max(0.0, 1.0 - done);
        return (int) Math.round(leftover * TOTAL_HOURS * 60.0);
    }

    public static String formatRemaining(int minutes) {
        if (minutes <= 0) {
            return "";
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours > 0 && mins > 0) {
            return hours + "h" + mins + "m";
        }
        if (hours > 0) {
            return hours + "h";
        }
        return mins + "m";
    }

    public static String overlayText(double done, String cap) {
        int percent = (int) (done * 100);
        if (!isTubWindow(cap)) {
            return percent + "%";
        }
        String remaining = formatRemaining(remainingMinutes(done));
        if (remaining.isEmpty()) {
            return percent + "%";
        }
        return percent + "% " + remaining;
    }
}
