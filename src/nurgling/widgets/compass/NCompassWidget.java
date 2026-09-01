package nurgling.widgets.compass;

import haven.Coord;
import haven.GOut;
import haven.UI;
import nurgling.conf.NDragProp;
import nurgling.conf.NResizeProp;
import nurgling.widgets.NDraggableWidget;

public class NCompassWidget extends NDraggableWidget {
    public static final String NAME = "compass";
    private static final int EDGE = 8;

    private UI.Grab resizeGrab;
    private NCompassResize.Edge resizeEdge;
    private int startLeft;
    private int startRight;

    public NCompassWidget(nurgling.NGameUI gui) {
        super(new NCompassBar(gui), NAME, restoredSize());
    }

    private static Coord restoredSize() {
        Coord saved = NResizeProp.find(NAME);
        int width = saved == null ? UI.scale(520) : saved.x;
        width = Math.max(UI.scale(300), Math.min(UI.scale(900), width));
        return new Coord(width, NCompassBar.contentHeight() + delta.y);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if (ev.b == 1 && ui.modctrl && !btnLock.a && resizeGrab == null && ui.grabs.isEmpty()) {
            NCompassResize.Edge edge = edgeAt(ev.c);
            if (edge != null) {
                resizeEdge = edge;
                startLeft = rootpos().x;
                startRight = startLeft + sz.x;
                resizeGrab = ui.grabmouse(this);
                parent.setfocus(this);
                return true;
            }
        }
        return super.mousedown(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        if (resizeGrab != null) {
            int min = UI.scale(300);
            int max = UI.scale(900);
            if (parent != null && parent.sz.x > 0)
                max = Math.max(min, Math.min(max, parent.sz.x));
            NCompassResize.Result result = NCompassResize.drag(
                    resizeEdge, startLeft, startRight, ui.mc.x, min, max);
            int parentLeft = parent == null ? result.left : parent.rootxlate(new Coord(result.left, rootpos().y)).x;
            c.x = parentLeft;
            target_c.x = parentLeft;
            resize(new Coord(result.width, NCompassBar.contentHeight() + delta.y));
            return;
        }
        super.mousemove(ev);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if (ev.b == 1 && resizeGrab != null) {
            resizeGrab.remove();
            resizeGrab = null;
            target_c.x = c.x;
            target_c.y = c.y;
            NResizeProp.set(name, new NResizeProp(new Coord(sz), name));
            NDragProp prop = new NDragProp(new Coord(target_c), btnLock.a, btnVis.a, name);
            prop.flip = btnFlip.a;
            NDragProp.set(name, prop);
            return true;
        }
        return super.mouseup(ev);
    }

    @Override
    public void draw(GOut g) {
        super.draw(g);
        Coord mouse = ui.mc.sub(rootpos());
        NCompassResize.Edge edge = edgeAt(mouse);
        if (resizeGrab != null || (ui.modctrl && edge != null)) {
            NCompassResize.Edge active = resizeGrab != null ? resizeEdge : edge;
            int x = active == NCompassResize.Edge.LEFT ? 1 : sz.x - 2;
            g.chcolor(255, 221, 120, 230);
            g.line(new Coord(x, 0), new Coord(x, sz.y - 1), UI.scale(2));
            g.chcolor();
        }
    }

    private NCompassResize.Edge edgeAt(Coord point) {
        if (point.y < 0 || point.y >= sz.y)
            return null;
        int edge = UI.scale(EDGE);
        if (point.x >= 0 && point.x <= edge)
            return NCompassResize.Edge.LEFT;
        if (point.x < sz.x && point.x >= sz.x - edge)
            return NCompassResize.Edge.RIGHT;
        return null;
    }
}
