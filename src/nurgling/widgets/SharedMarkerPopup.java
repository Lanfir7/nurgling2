package nurgling.widgets;

import haven.Button;
import haven.Coord;
import haven.GOut;
import haven.Label;
import haven.UI;
import haven.Widget;
import nurgling.i18n.L10n;

public class SharedMarkerPopup extends Widget {
    private final Runnable accepted;
    private final Runnable declined;

    public SharedMarkerPopup(String markerName, Runnable accepted, Runnable declined) {
        super(UI.scale(390, 116));
        this.accepted = accepted;
        this.declined = declined;

        add(new Label(L10n.get("marker.clipboard.title")), UI.scale(14, 10));
        add(new Label(L10n.get("marker.clipboard.offer")), UI.scale(14, 34));
        Label name = add(new Label("«" + shorten(markerName) + "»"), UI.scale(14, 56));
        name.setcolor(new java.awt.Color(150, 205, 255));
        add(new Button(UI.scale(80), L10n.get("marker.clipboard.yes"), false) {
            public void click() {
                close(SharedMarkerPopup.this.accepted);
            }
        }, UI.scale(210, 81));
        add(new Button(UI.scale(80), L10n.get("marker.clipboard.no"), false) {
            public void click() {
                close(SharedMarkerPopup.this.declined);
            }
        }, UI.scale(296, 81));
    }

    private static String shorten(String name) {
        if (name == null)
            return "";
        return name.length() <= 42 ? name : name.substring(0, 39) + "...";
    }

    private void close(Runnable action) {
        reqdestroy();
        action.run();
    }

    @Override
    public void draw(GOut g) {
        g.chcolor(20, 27, 34, 245);
        g.frect(Coord.z, sz);
        g.chcolor(93, 138, 181, 255);
        g.rect(Coord.z, sz.sub(1, 1));
        g.chcolor();
        super.draw(g);
    }
}
