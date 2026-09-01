package nurgling.widgets;

import haven.Text;
import haven.UI;

import java.awt.Color;
import java.awt.Font;

final class ForageSeasonPresentation {
    private static final Color YES = new Color(90, 220, 105);
    private static final Color NO = new Color(235, 85, 85);
    private static final Color CONDITIONAL = new Color(235, 205, 95);
    private static final Color UNKNOWN = new Color(165, 165, 165);
    private static final Font SYMBOL_FONT =
            new Font(Font.SANS_SERIF, Font.BOLD, UI.scale(12));
    private static final Text.Foundry SYMBOL_FOUNDRY =
            new Text.Foundry(SYMBOL_FONT).aa(true);

    private ForageSeasonPresentation() {
    }

    static String glyph(String value) {
        if("Y".equals(value) || "(Y)".equals(value))
            return "✓";
        if("N".equals(value))
            return "✕";
        return value;
    }

    static Font symbolFont() {
        return SYMBOL_FONT;
    }

    static Text.Line render(String value) {
        return SYMBOL_FOUNDRY.render(glyph(value), color(value));
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
