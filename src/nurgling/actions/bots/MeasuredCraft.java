package nurgling.actions.bots;

import nurgling.tools.VSpec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flour and water amounts on a craft slot are hundredths of a kilogram or litre
 * (35 means 0.35), not a count of inventory items.
 */
public final class MeasuredCraft {
    public static final int HUNDREDTHS_PER_ITEM = 100;
    private static final Pattern WATER = Pattern.compile(
            "([0-9]*\\.?[0-9]+)\\s*l\\s+of\\s+Water\\b", Pattern.CASE_INSENSITIVE);

    private MeasuredCraft() {}

    public static boolean isWater(String name) {
        return name != null && name.equalsIgnoreCase("Water");
    }

    /** Flour, and any item the client files under the Flour category. */
    public static boolean pricedByWeight(String name) {
        if (name == null)
            return false;
        if (isWater(name) || "Flour".equalsIgnoreCase(name))
            return true;
        for (String category : VSpec.getCategory(name)) {
            if ("Flour".equals(category))
                return true;
        }
        return false;
    }

    /** How many 1.00 kg/l items cover this many hundredths. */
    public static int itemsToCover(long hundredths) {
        if (hundredths <= 0)
            return 0;
        long items = (hundredths + HUNDREDTHS_PER_ITEM - 1) / HUNDREDTHS_PER_ITEM;
        return items > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) items;
    }

    public static int slotsFor(long hundredths, int stackSize) {
        int items = itemsToCover(hundredths);
        int stack = Math.max(1, stackSize);
        return (items + stack - 1) / stack;
    }

    /** "1.50 l of Water" -> 150. Salt water and other liquids are not craft water. */
    public static int waterHundredths(String contentName) {
        if (contentName == null)
            return 0;
        Matcher match = WATER.matcher(contentName);
        if (!match.find())
            return 0;
        try {
            return (int) Math.round(Double.parseDouble(match.group(1)) * HUNDREDTHS_PER_ITEM);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
