package nurgling.tools;

/**
 * Pure helpers for craft-window average-quality labels.
 * No client types — unit-testable without Haven.
 */
public final class CraftSlotQuality {
    /** Extra unscaled pixels under ingredient/result icons for the quality line. */
    public static final int LINE = 12;

    private CraftSlotQuality() {}

    /**
     * Arithmetic mean of per-slot averages. Empty slots ({@code null}) are skipped.
     * Recipe count does not weight a slot.
     */
    public static Double meanOfSlotAverages(Iterable<Double> slotAverages) {
        return average(slotAverages);
    }

    /** Arithmetic mean; {@code null} entries skipped. No values → {@code null} (do not show 0). */
    public static Double average(Iterable<Double> values) {
        if (values == null) {
            return null;
        }
        double sum = 0;
        int n = 0;
        for (Double v : values) {
            if (v == null) {
                continue;
            }
            sum += v.doubleValue();
            n++;
        }
        if (n == 0) {
            return null;
        }
        return Double.valueOf(sum / n);
    }

    public static Double qualityOf(Float q) {
        if (q == null || q.floatValue() <= 0f) {
            return null;
        }
        return Double.valueOf(q.doubleValue());
    }

    /**
     * Match a player-inventory item to a recipe slot.
     * When any highlighted {@code MakePrep} item exists, only those are used;
     * otherwise fall back to name matching.
     */
    public static boolean includeItem(boolean itemHasMakePrep, boolean anyMakePrep,
                                      String itemName, String slotName) {
        if (itemName == null || slotName == null) {
            return false;
        }
        if (!slotName.equals(itemName)) {
            return false;
        }
        if (anyMakePrep) {
            return itemHasMakePrep;
        }
        return true;
    }

    /** Resource-loaded MakePrep may not be {@code NMakewindow.MakePrep}. */
    public static boolean isMakePrepClass(String className) {
        return className != null && className.endsWith("MakePrep");
    }

    /** Auto/search: use the picked category ingredient when present. */
    public static String slotMatchName(String specName, String selectedIngName) {
        if (selectedIngName != null) {
            return selectedIngName;
        }
        return specName;
    }

    /**
     * Height after pack: content plus one quality line, never stacked.
     * If children already filled the previous packed size, the line is already
     * in {@code contentHeight} and must not be added again.
     */
    public static int packedHeight(int contentHeight, int currentHeight, int linePx) {
        if (linePx < 0) {
            linePx = 0;
        }
        if (currentHeight > 0 && contentHeight == currentHeight) {
            return contentHeight;
        }
        return contentHeight + linePx;
    }
}
