package nurgling.widgets.nsettings;

import haven.Button;
import haven.Coord;

/** Button that keeps enough horizontal room for the current localized text. */
class HotkeyTextButton extends Button {
    private final int minimumWidth;

    HotkeyTextButton(int minimumWidth, String label) {
        super(minimumWidth, label, false);
        this.minimumWidth = minimumWidth;
        fitToText();
    }

    private void fitToText() {
        int width = Math.max(minimumWidth, text.sz().x + margin);
        if(sz.x != width) {
            resize(Coord.of(width, sz.y));
            redraw();
        }
    }

}
