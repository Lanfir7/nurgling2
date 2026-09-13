package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.ICheckBox;
import haven.UI;
import nurgling.news.ReleaseNotes;

import java.awt.Color;

/** Main-menu launcher with a lightweight unread indicator. */
public class ReleaseNotesMenuButton extends ICheckBox {
    private static final Color UNREAD_COLOR = new Color(225, 180, 90);

    public ReleaseNotesMenuButton(Runnable action) {
        super("nurgling/hud/buttons/rbtn/news/", "u", "d", "h", "dh");
        click(action);
        settip(nurgling.i18n.L10n.get("news.menu"));
    }

    @Override
    public void draw(GOut g) {
        super.draw(g);
        if (ReleaseNotes.hasUnread()) {
            int radius = UI.scale(3);
            Coord center = Coord.of(sz.x - UI.scale(10), UI.scale(10));
            g.chcolor(UNREAD_COLOR);
            g.fellipse(center, Coord.of(radius));
            g.chcolor();
        }
    }
}
