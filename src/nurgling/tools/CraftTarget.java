package nurgling.tools;

/**
 * Parse and clamp the Crafting window quantity field.
 * Empty → 1; a positive integer → that many crafts; anything else is invalid.
 */
public final class CraftTarget {
    /** Craft All: keep going until materials run out. */
    public static final int ALL = 9999;

    private CraftTarget() {}

    /**
     * @return the target count, or {@code null} if the text is not a valid count
     */
    public static Integer parse(String text) {
        if (text == null)
            return Integer.valueOf(1);
        String cand = text.trim();
        if (cand.isEmpty())
            return Integer.valueOf(1);
        try {
            int n = Integer.parseInt(cand);
            if (n < 1)
                return null;
            return Integer.valueOf(n);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isAll(int count) {
        return count >= ALL;
    }

    /**
     * Cap a planned batch by the stored target. Craft All does not cap.
     */
    public static int capIterations(int stored, int planned) {
        if (stored < 1 || planned < 1)
            return 0;
        if (isAll(stored))
            return planned;
        return Math.min(stored, planned);
    }

    /** Whether {@code crafted} has already hit a finite stored target. */
    public static boolean reachedCap(int crafted, int stored) {
        if (isAll(stored))
            return false;
        return crafted >= stored;
    }
}
