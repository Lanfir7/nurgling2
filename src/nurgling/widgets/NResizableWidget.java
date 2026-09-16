package nurgling.widgets;

import haven.*;
import nurgling.*;
import nurgling.conf.*;

import java.util.*;

public class NResizableWidget extends NDraggableWidget
{
    public NResizableWidget(Widget content, String name, Coord sz)
    {
        super(content, name, NResizeProp.find(name) != null ? Objects.requireNonNull(NResizeProp.find(name)) : sz);
        this.basesz = new Coord(sz);
    }


    public NResizableWidget(String name)
    {
        super(name, NResizeProp.find(name) != null ? Objects.requireNonNull(NResizeProp.find(name)) : new Coord(200, 200));
        this.basesz = new Coord(200, 200);
    }

    /** A panel that can be stretched lays its content out at the frame size instead. */
    @Override
    protected boolean stretchcontent()
    {
        return(true);
    }

    /**
     * A frame whose content runs all the way to its right and bottom edges. Used
     * by the minimap, where the strip normally reserved for the lock and
     * visibility controls was simply an empty gap next to the map; the controls
     * are drawn over the map instead, and only while the HUD is being edited.
     */
    public static class Flush extends NResizableWidget
    {
        public Flush(Widget content, String name, Coord sz)
        {
            super(content, name, sz);
        }

        @Override
        protected Coord contentDelta()
        {
            return(Coord.z);
        }

        /* Flush against the corner too: the inset at the top and left was the same empty
         * border as the one on the right, just harder to notice until the panel was put
         * against the edge of the screen. */
        @Override
        protected Coord contentOff()
        {
            return(Coord.z);
        }
    }

    public static final Tex sizeru = Resource.loadtex("nurgling/hud/wnd/sizer/u");
    public static final Tex sizerd = Resource.loadtex("nurgling/hud/wnd/sizer/d");
    public static final Tex sizerh = Resource.loadtex("nurgling/hud/wnd/sizer/h");
    public Coord minSize = new Coord(200,200);

    /**
     * Width of the grab zone inside the frame edge. A drag that starts there
     * stretches the window from that edge or corner; anywhere further in moves the
     * whole window, which is how every other window on the desktop behaves.
     */
    private static final int border = UI.scale(8);

    private UI.Grab drag;
    private NWindowResize.Edge dragedge = NWindowResize.Edge.NONE;
    private NWindowResize.Edge hoveredge = NWindowResize.Edge.NONE;
    /** Cursor and geometry at the start of a stretch, all in parent coordinates. */
    private Coord dragstart, startc, startsz;

    /** Stretching runs from half the default size up to three times it. */
    @Override
    protected double scalemax() {
        return(3.0);
    }

    /** Current size as a fraction of the default one. */
    @Override
    public double scale() {
        return((basesz.x <= 0) ? 1.0 : ((double)sz.x / basesz.x));
    }

    /** Resize to a fraction of the default size, keeping the top-left corner in place. */
    @Override
    public void scale(double s) {
        s = Utils.clip(s, scalemin, scalemax());
        Coord nsz = new Coord(Math.max((int)Math.round(basesz.x * s), UI.scale(minSize.x)),
                              Math.max((int)Math.round(basesz.y * s), UI.scale(minSize.y)));
        if(nsz.equals(sz))
            return;
        super.resize(nsz);
        NResizeProp.set(name, new NResizeProp(new Coord(sz), name));
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        /* Plain left button while editing the HUD, or the adjust gesture mid-game. A bare
         * click in play never resizes: outside DRAG mode the edges belong to the content,
         * which on a flush frame is the map itself. */
        boolean grab = ((ev.b == 1) && (ui.core.mode == NCore.Mode.DRAG)) || adjustclick(ev);
        if(grab && (drag == null) && !btnLock.a) {
            NWindowResize.Edge edge = NWindowResize.hit(ev.c, sz, border);
            if(edge != NWindowResize.Edge.NONE) {
                drag = ui.grabmouse(this);
                dragedge = edge;
                dragstart = ev.c.add(c);
                startc = new Coord(c);
                startsz = new Coord(sz);
                return(true);
            }
        }
        return super.mousedown(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        if(drag != null) {
            /* Via parent coordinates, because dragging a left or top edge moves the
             * window and would otherwise shift the cursor's own frame of reference. */
            Coord delta = ev.c.add(c).sub(dragstart);
            NWindowResize.Result res = NWindowResize.drag(dragedge, startc, startsz, delta,
                                                          UI.scale(minSize));
            resize(res.size);
            /* The edge opposite the one being dragged has to stay where it is, so the
             * window follows the anchor point rather than keeping its old corner. */
            placeAt(res.position);
            NResizeProp.set(name, new NResizeProp(NResizableWidget.this.sz, name));
        }
        super.mousemove(ev);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        /* Recomputed from the cursor rather than from move events, so the highlight also
         * goes away when the pointer leaves the window entirely. */
        hoveredge = ((drag == null) && !btnLock.a && editing() && (sz != Coord.z) && (ui.mc != null))
            ? NWindowResize.hit(ui.mc.sub(rootpos()), sz, border) : NWindowResize.Edge.NONE;
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if((ev.b == 1) && (drag != null)) {
            drag.remove();
            drag = null;
            dragedge = NWindowResize.Edge.NONE;
            savePlacement();
            NResizeProp.set(name, new NResizeProp(NResizableWidget.this.sz, name));
            return(true);
        }
        return super.mouseup(ev);
    }


    @Override
    public void resize(Coord sz)
    {
        if(drag!=null)
            super.resize(sz);
        else
        {
            ArrayList<NResizeProp> resizeProps = ((ArrayList<NResizeProp>) NConfig.get(NConfig.Key.resizeprop));
            if (resizeProps == null)
                resizeProps = new ArrayList<>();
            for (NResizeProp prop : resizeProps)
            {
                if (prop.name.equals(name))
                {
                    super.resize(prop.sz);
                }
            }
        }
    }

    @Override
    public void draw(GOut g) {
        super.draw(g);
        NWindowResize.Edge edge = (drag != null) ? dragedge : hoveredge;
        if(!btnLock.a)
            drawgrab(g, edge);
        /* The corner grip belongs to the editing mode; mid-game the lit edge is the whole
         * hint, so holding the modifier does not sprinkle grips over the screen. */
        if(ui.core.mode != NCore.Mode.DRAG)
            return;
        Coord sc = sz.sub(sizerd.sz()).sub(NStyle.locki[0].sz().x / 2, UI.scale(8));
        if(drag != null) {
            if(!btnLock.a)
                g.image(sizerd, sc);
        } else if(edge != NWindowResize.Edge.NONE) {
            g.image(sizerh, sc);
        } else {
            g.image(sizeru, sc);
        }
    }

    /** Light up the grab zone under the cursor, so it is clear what a drag will stretch. */
    private void drawgrab(GOut g, NWindowResize.Edge edge) {
        if(edge == NWindowResize.Edge.NONE)
            return;
        g.chcolor(255, 216, 96, 120);
        if(NWindowResize.left(edge))
            g.frect(Coord.z, new Coord(border, sz.y));
        if(NWindowResize.right(edge))
            g.frect(new Coord(sz.x - border, 0), new Coord(border, sz.y));
        if(NWindowResize.top(edge))
            g.frect(Coord.z, new Coord(sz.x, border));
        if(NWindowResize.bottom(edge))
            g.frect(new Coord(0, sz.y - border), new Coord(sz.x, border));
        g.chcolor();
    }

}
