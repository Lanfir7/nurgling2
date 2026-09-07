package nurgling.widgets.nsettings;

import haven.Button;
import haven.Coord;

/** Button that keeps enough horizontal room for the current localized text. */
class HotkeyTextButton extends Button {
    private final int minimumWidth;

    HotkeyTextButton(int minimumWidth, String label) {
        super(fittedWidth(minimumWidth, label), label, false);
        this.minimumWidth = minimumWidth;
    }

    private static int fittedWidth(int minimumWidth, String label) {
        return Math.max(minimumWidth,
                nurgling.fonts.FontTheme.foundry(DEFAULT_FONT_ROLE).strsize(label).x + margin);
    }

    private void fitToText() {
        int width = Math.max(minimumWidth, text.sz().x + margin);
        if(sz.x != width) {
            resize(Coord.of(width, sz.y));
            redraw();
        }
    }

    @Override
    public void fontThemeChanged(long revision) {
        super.fontThemeChanged(revision);
        fitToText();
    }
}
