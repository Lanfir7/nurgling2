package nurgling.tools;

public final class QuestGiverDistance {
    public static final double TILE = 11.0;

    public static String withMeters(String line, Double meters) {
        if (line == null)
            return null;
        if (meters == null || meters < 0 || Double.isNaN(meters))
            return line;
        return line + " - " + Math.round(meters) + "m";
    }

    public static Double meters(double worldDist) {
        if (worldDist < 0 || Double.isNaN(worldDist))
            return null;
        return worldDist / TILE;
    }

    public static int compareMeters(Double a, Double b) {
        if (a == null && b == null)
            return 0;
        if (a == null)
            return 1;
        if (b == null)
            return -1;
        return Double.compare(a, b);
    }

    public static boolean namesMatch(String giver, String tip) {
        if (giver == null || giver.isEmpty() || tip == null || tip.isEmpty())
            return false;
        if (tip.equals(giver))
            return true;
        return tip.startsWith(giver + " ") || tip.startsWith(giver + "(");
    }
}
