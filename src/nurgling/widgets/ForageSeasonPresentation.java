package nurgling.widgets;

import java.awt.Color;

final class ForageSeasonPresentation {
    private static final Color YES = new Color(90, 220, 105);
    private static final Color NO = new Color(235, 85, 85);
    private static final Color CONDITIONAL = new Color(235, 205, 95);
    private static final Color UNKNOWN = new Color(165, 165, 165);

    private ForageSeasonPresentation() {
    }

    static String glyph(String value) {
        if("Y".equals(value))
            return "✓";
        if("N".equals(value))
            return "✕";
        return value;
    }

    static Color color(String value) {
        if("Y".equals(value))
            return YES;
        if("N".equals(value))
            return NO;
        if("?".equals(value) || "...".equals(value))
            return UNKNOWN;
        return CONDITIONAL;
    }
}
