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
    private int startContentWidth;

    public NCompassWidget(nurgling.NGameUI gui) {
        super(new NCompassBar(gui), NAME, restoredSize());
    }

    public void applyCompassVisibility(boolean visible) {
        btnVis.a = visible;
        if (content != null)
            content.visible = visible;
        if (visible)
            show();
        else
            hide();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        btnVis.hide();
    }

    private static Coord restoredSize() {
        Coord saved = NResizeProp.find(NAME);
        int contentWidth = saved == null ? UI.scale(520) : saved.x - delta.x;
        contentWidth = Math.max(UI.scale(300), Math.min(UI.scale(900), contentWidth));
        return new Coord(contentWidth + delta.x, NCompassBar.contentHeight() + delta.y);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if (ev.b == 1 && ui.modctrl && !btnLock.a && resizeGrab == null && ui.grabs.isEmpty()) {
            NCompassResize.Edge edge = edgeAt(ev.c);
            if (edge != null) {
                resizeEdge = edge;
                startLeft = rootpos().x;
                startContentWidth = content.sz.x;
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
                max = Math.max(min, Math.min(max, parent.sz.x - delta.x));
            NCompassResize.Result result = NCompassResize.dragFrame(
                    resizeEdge, startLeft, off.x, startContentWidth, ui.mc.x,
                    min, max, delta.x);
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
            int x = active == NCompassResize.Edge.LEFT ? off.x : off.x + content.sz.x - 1;
            g.chcolor(255, 221, 120, 230);
            g.line(new Coord(x, 0), new Coord(x, sz.y - 1), UI.scale(2));
            g.chcolor();
        }
    }

    private NCompassResize.Edge edgeAt(Coord point) {
        if (point.y < 0 || point.y >= sz.y)
            return null;
        int edge = UI.scale(EDGE);
        int left = off.x;
        int right = off.x + (content == null ? sz.x - delta.x : content.sz.x);
        if (point.x >= left - edge && point.x <= left + edge)
            return NCompassResize.Edge.LEFT;
        if (point.x >= right - edge && point.x <= right + edge)
            return NCompassResize.Edge.RIGHT;
        return null;
    }
}
