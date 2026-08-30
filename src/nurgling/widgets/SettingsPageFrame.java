package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.Label;
import haven.UI;
import haven.Widget;
import nurgling.NStyle;
import nurgling.widgets.nsettings.Panel;

public class SettingsPageFrame extends Widget {
    public final Panel panel;
    private final Label title;
    private Coord viewport = null;

    public SettingsPageFrame(Panel panel, String title) {
        this.panel = add(panel, Coord.z);
        this.title = (title == null) ? null : add(new Label(title), Coord.z);
    }

    public void fitTo(Coord viewport, int columns) {
        this.viewport = viewport;
        int pad = UI.scale(12);
        int innerWidth = Math.max(1, viewport.x - (pad * 2));
        int panelTop = pad;
        if(title != null) {
            title.move(Coord.of(pad, pad));
            panelTop = title.c.y + title.sz.y + pad;
        }
        int panelHeight = Math.max(1, viewport.y - panelTop - pad);
        if(panel instanceof AdaptiveSettingsPanel)
            ((AdaptiveSettingsPanel)panel).fitToViewport(
                    Coord.of(innerWidth, panelHeight), columns);
        else
            panel.resize(Coord.of(innerWidth, panel.sz.y));
        panel.move(Coord.of(pad, panelTop));

        updateFrameSize();
    }

    @Override
    public void cresize(Widget child) {
        if(child == panel && viewport != null)
            updateFrameSize();
    }

    private void updateFrameSize() {
        int pad = UI.scale(12);
        boolean ownsScroll = (panel instanceof AdaptiveSettingsPanel)
                && ((AdaptiveSettingsPanel)panel).ownsVerticalScroll();
        int contentBottom = panel.c.y + contentHeight(panel, pad);
        resize(Coord.of(viewport.x,
                ownsScroll ? viewport.y : Math.max(viewport.y, contentBottom)));
    }

    public static int contentHeight(Widget root, int bottomPadding) {
        return contentBottom(root) + bottomPadding;
    }

    private static int contentBottom(Widget root) {
        int bottom = root.sz.y;
        for(Widget child = root.child; child != null; child = child.next) {
            if(!child.visible)
                continue;
            bottom = Math.max(bottom, child.c.y + contentBottom(child));
        }
        return bottom;
    }

    @Override
    public void draw(GOut g) {
        g.chcolor(NStyle.infoBg);
        g.frect(Coord.z, sz);
        g.chcolor(NStyle.separator);
        g.rect(Coord.z, sz.sub(1, 1));
        g.chcolor();
        super.draw(g);
    }
}
