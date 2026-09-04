package nurgling;

import java.util.Locale;

/**
 * Overlay-only AttrMod percent/flat formatting for NTooltip.
 * Haven-free so tests do not need a live tooltip.
 */
public final class OverlayAttrModFormat {
    private OverlayAttrModFormat() {
    }

    public static boolean isPercentageAttribute(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            if (isPercentageClassName(current.getSimpleName()) || isPercentageClassName(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    static boolean isPercentageClassName(String name) {
        if (name == null) {
            return false;
        }
        return name.contains("normattr") || name.contains("inormattr") || name.contains("pmattr");
    }

    public static String formatModValue(double modValue, boolean isPercent) {
        String sign = modValue >= 0 ? "+" : "";
        if (isPercent) {
            double percent = Math.abs(modValue) >= 1 ? modValue : modValue * 100;
            if (percent == Math.floor(percent)) {
                return String.format(Locale.US, "%s%.0f%%", sign, percent);
            }
            return String.format(Locale.US, "%s%.1f%%", sign, percent);
        }
        return String.format(Locale.US, "%s%d", sign, (int) modValue);
    }
}
