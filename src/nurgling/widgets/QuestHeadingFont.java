package nurgling.widgets;

import haven.Text;

import java.awt.Color;
import java.awt.Font;

/** Shared heading font used by Quest Helper and compact account/session lists. */
public final class QuestHeadingFont {
    private QuestHeadingFont() {}

    public static Text.Foundry from(Text.Foundry base) {
        if (base == null)
            base = new Text.Foundry(Text.sans, 12);
        return new Text.Foundry(base.font.deriveFont(Font.BOLD), Color.WHITE).aa(true);
    }
}
