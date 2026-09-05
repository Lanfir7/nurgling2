package nurgling.widgets.craftatlas;

import haven.Coord;
import haven.GOut;
import haven.UI;
import haven.Widget;

import java.awt.Color;
import java.util.function.IntConsumer;

/** Draggable divider between the recipe table and its detail pane. */
final class CraftAtlasPaneDivider extends Widget {
    private final IntConsumer moved;
    private final Runnable finished;
    private UI.Grab drag;

    CraftAtlasPaneDivider(IntConsumer moved, Runnable finished) {
        super(Coord.of(UI.scale(8), UI.scale(100)));
        this.moved = moved;
        this.finished = finished;
    }

    @Override public void draw(GOut g) {
        int center = sz.x / 2;
        g.chcolor(drag == null ? new Color(65, 76, 79, 190) : new Color(218, 166, 68, 235));
        g.frect(Coord.of(center - UI.scale(1), 0), Coord.of(UI.scale(2), sz.y));
        int gripY = Math.max(0, sz.y / 2 - UI.scale(18));
        g.chcolor(drag == null ? new Color(155, 166, 166, 180) : new Color(255, 214, 126, 245));
        for(int y = 0; y < 5; y++)
            g.frect(Coord.of(center - UI.scale(2), gripY + y * UI.scale(8)), Coord.of(UI.scale(4), UI.scale(2)));
        g.chcolor();
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1) return super.mousedown(ev);
        drag = ui.grabmouse(this);
        update(ev.c.x);
        return true;
    }

    @Override public void mousemove(MouseMoveEvent ev) {
        super.mousemove(ev);
        if(drag != null) update(ev.c.x);
    }

    @Override public boolean mouseup(MouseUpEvent ev) {
        if(ev.b != 1 || drag == null) return super.mouseup(ev);
        drag.remove();
        drag = null;
        if(finished != null) finished.run();
        return true;
    }

    private void update(int localX) {
        if(moved != null) moved.accept(c.x + localX);
    }
}
