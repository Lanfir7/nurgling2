package nurgling.widgets;

import nurgling.hotkeys.Hotkeys;

import haven.*;
import haven.Window;
import nurgling.*;
import nurgling.conf.*;

import java.awt.*;

import static nurgling.widgets.NCatSelection.fnd;

public class NDraggableWidget extends Widget implements Widget.CursorQuery.Handler
{
    protected final String name;
    private UI.Grab dm;
    private Coord doff;
    public Coord target_c;
    protected ICheckBox btnLock;
    protected ICheckBox btnVis;
    private boolean isFlipped = false;
    protected ICheckBox btnFlip;
    /**
     * True while this widget still follows the anchored default layout. It is
     * resolved against the live screen size every tick, so the default HUD
     * survives resolution and UI-scale changes; the first time the player
     * touches the widget it gains an absolute saved position instead.
     */
    private boolean usingDefault = false;
    /** Set in tick() while this widget overlaps another one in DRAG mode. */
    private boolean colliding = false;
    public static final IBox box = Window.wbox;

    /** Distance at which a dragged edge locks onto a neighbour's edge. */
    private static final int SNAP = UI.scale(9);
    /**
     * How far apart two widgets may be on the perpendicular axis and still snap
     * to each other. Without this, edges would magnetise to widgets on the far
     * side of the screen, which feels like the drag is fighting back.
     */
    private static final int SNAP_RANGE = UI.scale(48);

    /** Placement history for DRAG-mode undo, newest last. */
    private static final java.util.ArrayDeque<Object[]> undo = new java.util.ArrayDeque<>();
    private static final int UNDO_MAX = 32;

    public final static Coord off = new Coord(UI.scale(10,10));
    public final static Coord delta = new Coord(UI.scale(35,20));

    /** Natural size of this panel, i.e. the size it has at 100%. */
    protected Coord basesz;
    private double uiscale = 1.0;

    protected static final double scalemin = 0.5;
    /** The one scale control on screen, if any; only the panel under the cursor gets one. */
    private static ScaleSlider current;
    public Widget content = null;
    TexI label = null;
    public static Text.Furnace fnd = new PUtils.BlurFurn(new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 14, Color.YELLOW).aa(true), UI.scale(1), UI.scale(2), Color.BLACK);
    public NDraggableWidget(Widget content, String name, Coord sz)
    {
        this(name,sz);
        this.content = add(content);
        this.content.visible = btnVis.a;
        content.resize(this.sz.sub(contentDelta()));
        content.move(contentOff());
        if(!stretchcontent() && (uiscale != 1.0))
            applyscale();
    }

    /**
     * Whether the content follows the frame size. Panels built around a layout that
     * can reflow say yes and are simply given a new size; the rest keep their natural
     * size and are drawn magnified instead, which is the only way to make a panel of
     * fixed artwork - the belt, the equipment proxy, the alarms - any smaller.
     */
    protected boolean stretchcontent()
    {
        return(false);
    }

    /**
     * Frame space taken away from the content, i.e. how much smaller the content
     * is than the widget. The default leaves a strip on the right for the
     * lock/visibility controls; a subclass may reclaim it, in which case those
     * controls are drawn over the content while in DRAG mode.
     */
    /**
     * Where the content starts inside the frame. Normally inset so the frame's own
     * border and controls have room; a frame that reclaims that strip puts the content
     * flush against its corner instead.
     */
    protected Coord contentOff()
    {
        return(off);
    }

    protected Coord contentDelta()
    {
        return(delta);
    }

    public NDraggableWidget(String name, Coord sz)
    {
        label = new TexI(fnd.render(NDefaultLayout.title(name)).img);
        this.sz = sz;
        this.basesz = new Coord(sz);
        this.name = name;
        add(btnLock = new ICheckBox(NStyle.locki[0], NStyle.locki[1], NStyle.locki[2], NStyle.locki[3])
        {
            @Override
            public void changed(boolean val)
            {
                super.changed(val);
                persist();
            }
        }, new Coord(sz.x - NStyle.locki[0].sz().x - NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y / 2));

        add(btnVis = new ICheckBox(NStyle.visi[0], NStyle.visi[1], NStyle.visi[2], NStyle.visi[3])
        {
            @Override
            public void changed(boolean val)
            {
                super.changed(val);
                if(content != null) {
                    content.visible = val;
                }
                persist();
            }
        }, new Coord(sz.x - NStyle.locki[0].sz().x - NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y + off.y));

        add(btnFlip = new ICheckBox(NStyle.flipi[0], NStyle.flipi[1], NStyle.flipi[2], NStyle.flipi[3])
        {
            @Override
            public void changed(boolean val)
            {
                super.changed(val);
                if(content!=null)
                {
                    flipContent();
                }
                persist();
            }
        }, new Coord(NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y/2));

        btnVis.hide();
        btnLock.hide();
        btnFlip.hide();
//        this.sz = sz.add(new Coord(NStyle.locki[0].sz().x, 0));
        if (NDragProp.has(name))
        {
            NDragProp prop = NDragProp.get(name);
            this.c = new Coord(prop.c);
            this.target_c = new Coord(prop.c);
            this.btnLock.a = prop.locked;
            this.btnVis.a = prop.vis;
            this.btnFlip.a = prop.flip;
            if(!stretchcontent())
                this.uiscale = Utils.clip(prop.scale, scalemin, scalemax());
        }
        else
        {
            this.usingDefault = true;
            this.target_c = new Coord(Coord.z);
            this.btnVis.a = NDefaultLayout.defaultVis(name);
        }
        // Apply loaded visibility state to content if it exists
        if(content != null) {
            content.visible = btnVis.a;
        }
    }



    /**
     * Magnetise the dragged position onto the edges of nearby widgets so that
     * panels line up flush without pixel-hunting. Edge-to-edge and
     * edge-to-opposite-edge are both considered, which covers both "align these
     * two" and "stack these two".
     */
    private void snapNeighbours(Coord pos) {
        if(parent == null)
            return;
        int bestx = 0, besty = 0;
        int distx = SNAP + 1, disty = SNAP + 1;
        for(Widget wdg : parent.children()) {
            if(!(wdg instanceof NDraggableWidget) || (wdg == this) || !wdg.visible())
                continue;
            NDraggableWidget o = (NDraggableWidget)wdg;
            if(o.sz == Coord.z)
                continue;
            /* Only consider a neighbour that is roughly abreast of us on the
             * other axis, otherwise every widget on screen is a snap target. */
            if(near(pos.y, sz.y, o.c.y, o.sz.y)) {
                for(int mine : new int[]{pos.x, pos.x + sz.x}) {
                    for(int theirs : new int[]{o.c.x, o.c.x + o.sz.x}) {
                        int d = theirs - mine;
                        if(Math.abs(d) < Math.abs(distx)) {
                            distx = d;
                            bestx = d;
                        }
                    }
                }
            }
            if(near(pos.x, sz.x, o.c.x, o.sz.x)) {
                for(int mine : new int[]{pos.y, pos.y + sz.y}) {
                    for(int theirs : new int[]{o.c.y, o.c.y + o.sz.y}) {
                        int d = theirs - mine;
                        if(Math.abs(d) < Math.abs(disty)) {
                            disty = d;
                            besty = d;
                        }
                    }
                }
            }
        }
        if(Math.abs(distx) <= SNAP)
            pos.x += bestx;
        if(Math.abs(disty) <= SNAP)
            pos.y += besty;
    }

    private static boolean near(int a, int alen, int b, int blen) {
        return((a < (b + blen + SNAP_RANGE)) && (b < (a + alen + SNAP_RANGE)));
    }

    private boolean isect(NDraggableWidget o) {
        return((c.x < (o.c.x + o.sz.x)) && (o.c.x < (c.x + sz.x)) &&
               (c.y < (o.c.y + o.sz.y)) && (o.c.y < (c.y + sz.y)));
    }

    private void pushUndo() {
        undo.addLast(new Object[]{this, new Coord(target_c)});
        while(undo.size() > UNDO_MAX)
            undo.removeFirst();
    }

    /** Revert the most recent widget move. Returns false if there is nothing to undo. */
    public static boolean undoLast() {
        for(Object[] step = undo.pollLast(); step != null; step = undo.pollLast()) {
            NDraggableWidget wdg = (NDraggableWidget)step[0];
            /* History outlives widgets across relogs; skip anything detached. */
            if(wdg.parent == null)
                continue;
            Coord to = (Coord)step[1];
            wdg.target_c.x = to.x;
            wdg.target_c.y = to.y;
            wdg.c = new Coord(to);
            wdg.persist();
            return(true);
        }
        return(false);
    }

    @Override
    public boolean globtype(GlobKeyEvent ev) {
        if((ui.core.mode == NCore.Mode.DRAG) && Hotkeys.action(Hotkeys.LAYOUT_UNDO).current().matches(ev.awt, 0)) {
            /* Delivered to whichever draggable widget the traversal reaches
             * first; the history itself is shared, so it does not matter which. */
            return(undoLast());
        }
        return(super.globtype(ev));
    }

    /**
     * Store the current placement of this widget, which also opts it out of the
     * anchored default layout: once the player has expressed an intent for this
     * widget we must not keep moving it around on their behalf.
     */
    private void persist()
    {
        usingDefault = false;
        if(!(parent instanceof GameUI))
            return;
        NDragProp prop = new NDragProp(new Coord(target_c), btnLock.a, btnVis.a, name);
        prop.flip = btnFlip.a;
        prop.scale = uiscale;
        NDragProp.set(name, prop);
    }

    /**
     * True while the layout-adjust modifier is held. Panels offer their handles then,
     * so the HUD can be rearranged mid-game without entering the editing mode - and
     * stays untouchable during ordinary play, since a bare click never reaches this.
     */
    protected boolean adjustmod()
    {
        return(Hotkeys.action(Hotkeys.LAYOUT_ADJUST).current().modifiersHeld(ui.modflags()));
    }

    /** True when this click is the gesture that grabs a panel to move or stretch it. */
    protected boolean adjustclick(MouseDownEvent ev)
    {
        return(Hotkeys.action(Hotkeys.LAYOUT_ADJUST).current().matchesMouse(ev.b, ui.modflags()));
    }

    /** True while this widget may be moved or stretched at all. */
    protected boolean editing()
    {
        return((ui.core.mode == NCore.Mode.DRAG) || adjustmod());
    }

    /** True while the cursor is over this widget, wherever the last mouse event went. */
    protected boolean hovered()
    {
        return((ui != null) && (ui.mc != null) && (sz != Coord.z) && ui.mc.sub(rootpos()).isect(Coord.z, sz));
    }

    private void startmove(MouseDownEvent ev)
    {
        pushUndo();
        dm = ui.grabmouse(this);
        doff = ev.c;
        parent.setfocus(this);
    }

    /**
     * Move this widget while a drag is in progress. The position is not written to
     * the config yet; call {@link #savePlacement()} when the drag ends.
     */
    protected void placeAt(Coord nc)
    {
        target_c.x = nc.x;
        target_c.y = nc.y;
        c = new Coord(nc);
    }

    /** Write the current placement to the config, ending a drag. */
    protected void savePlacement()
    {
        persist();
    }

    /**
     * Discard every saved widget placement and put the whole HUD back on the
     * anchored default layout. This is the escape hatch for a layout that has
     * been dragged into an unusable state, or one inherited from a different
     * resolution.
     */
    public static void resetLayout(GameUI gui)
    {
        undo.clear();
        NConfig.set(NConfig.Key.dragprop, new java.util.ArrayList<NDragProp>());
        for(Widget wdg : gui.children())
        {
            if(wdg instanceof NDraggableWidget)
                ((NDraggableWidget)wdg).resetToDefault();
        }
    }

    /**
     * Re-read every widget placement from the config. Needed after the saved
     * layout is replaced wholesale (an import), since live widgets hold their
     * own copy of the position and would otherwise ignore it.
     */
    public static void reloadLayout(GameUI gui)
    {
        for(Widget wdg : gui.children())
        {
            if(wdg instanceof NDraggableWidget)
                ((NDraggableWidget)wdg).reload();
        }
    }

    private void reload()
    {
        if(!NDragProp.has(name))
        {
            resetToDefault();
            return;
        }
        NDragProp prop = NDragProp.get(name);
        usingDefault = false;
        target_c.x = prop.c.x;
        target_c.y = prop.c.y;
        c = new Coord(prop.c);
        btnLock.a = prop.locked;
        btnVis.a = prop.vis;
        if(content != null)
            content.visible = prop.vis;
        if(!stretchcontent())
        {
            uiscale = Utils.clip(prop.scale, scalemin, scalemax());
            applyscale();
        }
        if(isFlipped && (btnFlip.a != prop.flip))
        {
            btnFlip.a = prop.flip;
            flipContent();
        }
    }

    /** Drop any saved placement and return to the anchored default layout. */
    public void resetToDefault()
    {
        usingDefault = true;
        btnLock.a = false;
        btnVis.a = NDefaultLayout.defaultVis(name);
        if(content != null)
            content.visible = btnVis.a;
        if(!stretchcontent() && (uiscale != 1.0))
        {
            uiscale = 1.0;
            applyscale();
        }
    }

    @Override
    public void resize(Coord sz)
    {
        if(!stretchcontent())
        {
            /* Whoever resizes such a panel is telling us its natural size - it is the
             * content announcing a new layout - so the player's scale is re-applied on
             * top of it rather than being overwritten by it. */
            basesz = new Coord(sz);
            if(content != null)
            {
                content.resize(sz.sub(contentDelta()));
                content.move(contentOff());
            }
            applyscale();
            return;
        }
        frame(sz);
        if(content!=null)
        {
            content.resize(sz.sub(contentDelta()));
            content.move(contentOff());
        }
    }

    /** Set the frame size and keep the frame's own controls in their corners. */
    private void frame(Coord fsz)
    {
        super.resize(fsz);
        btnLock.move(new Coord(fsz.x - NStyle.locki[0].sz().x - NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y / 2));
        btnVis.move(new Coord(fsz.x - NStyle.locki[0].sz().x - NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y + off.y));
        if(isFlipped)
            btnFlip.move(new Coord(NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y/2));
    }

    private void applyscale()
    {
        frame(new Coord(Math.max(1, (int)Math.round(basesz.x * uiscale)),
                        Math.max(1, (int)Math.round(basesz.y * uiscale))));
    }

    /** How large this panel may be made, as a fraction of its natural size. */
    protected double scalemax()
    {
        return(2.0);
    }

    /** Current size as a fraction of the natural one. */
    public double scale()
    {
        return(uiscale);
    }

    /** Resize to a fraction of the natural size, keeping the top-left corner in place. */
    public void scale(double s)
    {
        s = Utils.clip(s, scalemin, scalemax());
        if(Math.abs(s - uiscale) < 0.001)
            return;
        uiscale = s;
        applyscale();
        persist();
    }

    /** True while the content is drawn magnified rather than laid out at the frame size. */
    protected boolean scaled()
    {
        return(!stretchcontent() && (content != null) && (Math.abs(uiscale - 1.0) > 0.001));
    }

    /**
     * Convert a point in this frame into the content's own coordinates. The content
     * believes it is still at its natural size, so everything it is told - clicks,
     * hovers, tooltips - has to be divided back down by the scale it is drawn at.
     */
    private Coord tocontent(Coord c)
    {
        return(new Coord((int)Math.round(c.x / uiscale), (int)Math.round(c.y / uiscale)).sub(content.c));
    }

    @Override
    public Coord xlate(Coord c, boolean in)
    {
        if(!scaled())
            return(c);
        return(in ? new Coord((int)Math.round(c.x * uiscale), (int)Math.round(c.y * uiscale))
                  : new Coord((int)Math.round(c.x / uiscale), (int)Math.round(c.y / uiscale)));
    }

    public static final Tex bg = Resource.loadtex("nurgling/hud/wnd/bg");
    private static final Tex ctl = Resource.loadtex("nurgling/hud/box/tl");

    /**
     * Draw the children, putting the content through a magnifying projection when the
     * panel is scaled. The content keeps drawing at its natural size and coordinates;
     * only the transform that turns those into screen pixels is different, so artwork
     * scales without the content knowing about it. Text reads {@link GOut#tfscale} and
     * re-rasterizes glyphs at the screen size so labels stay sharp.
     */
    @Override
    public void draw(GOut g, boolean strict)
    {
        if(!scaled())
        {
            super.draw(g, strict);
            return;
        }
        for(Widget wdg = child; wdg != null; wdg = wdg.next)
        {
            if(!wdg.visible)
                continue;
            if(wdg == content)
            {
                haven.render.Pipe def = g.basicstate();
                float f = Text.Furnace.scalekey(uiscale) / 100f;
                new MiniMap.Scale2D(g.tx, f).apply(def);
                /* A loose clip in unscaled space: the content's own rectangle is larger
                 * than the frame when shrunk, and the projection is what brings it back
                 * inside, so clipping to the frame here would cut the drawing short.
                 * tfscale lets text re-rasterize at the screen size instead of stretching
                 * a bitmap through this projection. */
                GOut cg = new GOut(g.out, def, g.root().sz());
                cg.tfscale = f;
                cg.tfbase = g.basicstate();
                cg.tforigin = g.tx;
                wdg.draw(cg.reclipl(g.tx.add(wdg.c), wdg.sz));
            }
            else
            {
                wdg.draw(strict ? g.reclip(wdg.c, wdg.sz) : g.reclipl(wdg.c, wdg.sz));
            }
        }
    }

    @Override
    public void draw(GOut g)
    {
        if (ui.core.mode == NCore.Mode.DRAG)
        {
            drawBg(g, sz, ui);
            box.draw(g, Coord.z, sz);
            if(colliding)
            {
                /* Overlapping panels are the single biggest source of a HUD that
                 * "looks broken", so make them impossible to miss while editing. */
                g.chcolor(220, 60, 50, 255);
                g.rect(Coord.z, sz);
                g.rect(new Coord(1, 1), sz.sub(2, 2));
                g.chcolor();
            }
        }
        super.draw(g);
        if (ui.core.mode == NCore.Mode.DRAG) {
            /* The content is added last and so draws over the controls; on a frame
             * that reclaims the right-hand strip they would otherwise be buried. */
            for(ICheckBox btn : new ICheckBox[]{btnLock, btnVis, btnFlip}) {
                if(btn.visible())
                    btn.draw(g.reclipl(btn.c, btn.sz));
            }
            g.aimage(label, sz.div(2), 0.5, 0.5);
        } else if ((dm != null) || (adjustmod() && hovered())) {
            /* Holding the modifier turns the panel under the cursor into a handle and
             * says so: an outline plus its name, without covering the game with it. */
            g.chcolor(btnLock.a ? new Color(150, 150, 150, 120) : new Color(255, 216, 96, 190));
            g.rect(Coord.z, sz);
            g.rect(new Coord(1, 1), sz.sub(2, 2));
            g.chcolor();
            g.chcolor(255, 255, 255, 190);
            g.aimage(label, sz.div(2), 0.5, 0.5);
            g.chcolor();
        }
    }

    /** True while this panel offers its scale control. */
    private boolean wantslider()
    {
        return(!btnLock.a && (parent != null) && (ui != null) && (ui.core.mode != NCore.Mode.DRAG)
               && visible() && adjustmod() && hovered());
    }

    /**
     * The scale control, a popup that behaves like a tooltip: it appears next to the
     * cursor, at a spot decided once, and stays there. Living outside the panel is the
     * whole point - a panel anchored to a screen edge walks away from its own corner as
     * it is resized, which would drag the knob out from under the cursor.
     */
    public static class ScaleSlider extends Widget
    {
        private static final Coord ssz = new Coord(UI.scale(238), UI.scale(64));
        private static final int pad = UI.scale(14);
        private static final int knobw = UI.scale(9);
        private static final Color fillcol = new Color(0, 138, 146);
        private static final Color fillhi = new Color(96, 226, 230);
        private static final Color knobcol = new Color(214, 248, 250);
        private static final Color tickcol = new Color(132, 150, 152);
        private static final Text.Foundry pctfnd =
            new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 15, new Color(255, 226, 138)).aa(true);
        private static final Text.Foundry hintfnd = new Text.Foundry(Text.sans, 9, new Color(168, 182, 184)).aa(true);

        private final NDraggableWidget tgt;
        private final Text minlbl, maxlbl;
        private UI.Grab grab;
        private Text pcttext = null;
        private int pctshown = -1;

        private ScaleSlider(NDraggableWidget tgt)
        {
            super(ssz);
            this.tgt = tgt;
            this.minlbl = hintfnd.render((int)Math.round(scalemin * 100) + "%");
            this.maxlbl = hintfnd.render((int)Math.round(tgt.scalemax() * 100) + "%");
        }

        private static Color alpha(Color col, int a)
        {
            return(new Color(col.getRed(), col.getGreen(), col.getBlue(), a));
        }

        private int trackx()
        {
            return(pad);
        }

        private int trackw()
        {
            return(ssz.x - (2 * pad));
        }

        private int tracky()
        {
            /* Far enough off the bottom that the range labels below it clear the frame. */
            return(ssz.y - UI.scale(24));
        }

        private void slideto(int x)
        {
            double frac = Utils.clip((double)(x - trackx()) / trackw(), 0.0, 1.0);
            tgt.scale(scalemin + (frac * (tgt.scalemax() - scalemin)));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev)
        {
            if((ev.b == 1) && (grab == null) && (ev.c.y >= (tracky() - UI.scale(12))))
            {
                grab = ui.grabmouse(this);
                slideto(ev.c.x);
                return(true);
            }
            /* Consumed either way: the control sits over the panel it belongs to, and a
             * stray click must not fall through into it. */
            return(true);
        }

        @Override
        public void mousemove(MouseMoveEvent ev)
        {
            if(grab != null)
                slideto(ev.c.x);
        }

        @Override
        public boolean mouseup(MouseUpEvent ev)
        {
            if(grab != null)
            {
                grab.remove();
                grab = null;
                return(true);
            }
            return(true);
        }

        @Override
        public void tick(double dt)
        {
            super.tick(dt);
            /* Held open while the knob is being dragged or the cursor is on it, so a
             * panel that shrinks away from under the cursor does not take the control
             * with it mid-drag. */
            boolean keep = (grab != null) || tgt.wantslider()
                || ((tgt.parent != null) && tgt.adjustmod() && (ui.mc != null) && ui.mc.sub(rootpos()).isect(Coord.z, sz));
            if(!keep)
            {
                if(current == this)
                    current = null;
                destroy();
            }
        }

        @Override
        public void draw(GOut g)
        {
            double frac = Utils.clip((tgt.scale() - scalemin) / (tgt.scalemax() - scalemin), 0.0, 1.0);
            int x = trackx(), w = trackw(), cy = tracky();
            int fill = (int)Math.round(w * frac), kx = x + fill;
            /* The client's own window palette - dark body, amber border, darker title
             * strip - so the control looks like the inventory panels rather than an
             * overlay of its own invention. */
            int hdr = UI.scale(27), bw = Math.max(1, UI.scale(1));
            /* Slightly see-through, since it floats over whatever is being resized and
             * should not hide the result. */
            g.chcolor(alpha(NStyle.windowBg, 200));
            g.frect(Coord.z, sz);
            g.chcolor(alpha(NStyle.titleBg, 210));
            g.frect(Coord.z, new Coord(sz.x, hdr));
            g.chcolor(NStyle.separator);
            g.frect(new Coord(0, hdr), new Coord(sz.x, bw));
            g.chcolor(alpha(NStyle.border, 225));
            g.rect(Coord.z, sz);
            g.chcolor();

            g.aimage(tgt.label, new Coord(pad, UI.scale(14)), 0, 0.5);
            int pct = (int)Math.round(tgt.scale() * 100);
            if((pcttext == null) || (pct != pctshown))
            {
                pctshown = pct;
                pcttext = pctfnd.render(pct + "%");
            }
            g.aimage(pcttext.tex(), new Coord(sz.x - pad, UI.scale(14)), 1, 0.5);

            /* Scale marks, with the neutral 100% picked out: a size is much easier to
             * judge against the default than against the ends of the range. */
            for(int p = 50; p <= (int)Math.round(tgt.scalemax() * 100); p += 25)
            {
                boolean unit = (p == 100);
                int tx = x + (int)Math.round(w * ((p / 100.0) - scalemin) / (tgt.scalemax() - scalemin));
                g.chcolor(unit ? new Color(255, 226, 138, 200) : new Color(tickcol.getRed(), tickcol.getGreen(), tickcol.getBlue(), 130));
                g.frect(new Coord(tx, cy - UI.scale(unit ? 9 : 7)), new Coord(1, UI.scale(unit ? 5 : 3)));
            }

            /* Groove: a dark channel with a lit lower lip, which is what makes it look
             * cut into the frame rather than painted on it. */
            g.chcolor(0, 0, 0, 220);
            g.frect(new Coord(x - UI.scale(1), cy - UI.scale(4)), new Coord(w + UI.scale(2), UI.scale(8)));
            g.chcolor(26, 34, 36, 255);
            g.frect(new Coord(x, cy - UI.scale(3)), new Coord(w, UI.scale(6)));
            g.chcolor(255, 255, 255, 26);
            g.frect(new Coord(x, cy + UI.scale(3)), new Coord(w, 1));

            if(fill > 0)
            {
                g.chcolor(fillcol);
                g.frect(new Coord(x, cy - UI.scale(3)), new Coord(fill, UI.scale(6)));
                g.chcolor(fillhi.getRed(), fillhi.getGreen(), fillhi.getBlue(), 120);
                g.frect(new Coord(x, cy - UI.scale(3)), new Coord(fill, UI.scale(2)));
            }

            /* Knob: a capsule with clipped ends and a glow, lit brighter while held. */
            int kh = UI.scale(8), ka = (grab != null) ? 90 : 45;
            g.chcolor(fillhi.getRed(), fillhi.getGreen(), fillhi.getBlue(), ka);
            g.frect(new Coord(kx - knobw, cy - kh - UI.scale(2)), new Coord(knobw * 2, (kh + UI.scale(2)) * 2));
            g.chcolor(0, 0, 0, 200);
            g.frect(new Coord(kx - (knobw / 2) - 1, cy - kh - 1), new Coord(knobw + 2, (kh * 2) + 2));
            g.chcolor(knobcol);
            g.frect(new Coord(kx - (knobw / 2), cy - kh), new Coord(knobw, kh * 2));
            g.chcolor(fillcol);
            g.frect(new Coord(kx - (knobw / 2), cy - UI.scale(1)), new Coord(knobw, UI.scale(2)));
            g.chcolor();

            g.aimage(minlbl.tex(), new Coord(x, cy + UI.scale(12)), 0, 0.5);
            g.aimage(maxlbl.tex(), new Coord(x + w, cy + UI.scale(12)), 1, 0.5);
        }
    }

    public static void drawBg(GOut g, Coord sz, UI ui) {
        Coord bgUl = new Coord(ctl.sz().x / 2, ctl.sz().y / 2);
        Coord bgSz = new Coord(sz.x - ctl.sz().x, sz.y - ctl.sz().y);
        
        if (ui instanceof nurgling.NUI) {
            nurgling.NUI nui = (nurgling.NUI)ui;
            float opacity = nui.getUIOpacity();
            int alpha = (int)(255 * opacity);
            
            if (nui.getUseSolidBackground()) {
                // Use custom background color
                java.awt.Color bgColor = nui.getWindowBackgroundColor();
                g.chcolor(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), alpha);
                g.frect(bgUl, bgSz);
                g.chcolor();
            } else {
                // Use Window.bg texture with opacity
                g.chcolor(255, 255, 255, alpha);
                Coord bgc = new Coord();
                Coord ca_ul = bgUl;
                Coord ca_br = bgUl.add(bgSz);
                for(bgc.y = ca_ul.y; bgc.y < ca_br.y; bgc.y += Window.bg.sz().y) {
                    for(bgc.x = ca_ul.x; bgc.x < ca_br.x; bgc.x += Window.bg.sz().x)
                        g.image(Window.bg, bgc, ca_ul, ca_br);
                }
                g.chcolor();
            }
        } else {
            // Fallback
            int x_pos = ctl.sz().x;
            int y_pos = ctl.sz().y;
            for (int x = ctl.sz().x / 2; x + bg.sz().x < sz.x - ctl.sz().x / 2; x += bg.sz().x)
            {
                for (int y = ctl.sz().y / 2; y + bg.sz().y < sz.y - ctl.sz().y / 2; y += bg.sz().y)
                {
                    g.image(bg, new Coord(x, y));
                    y_pos = Math.max(y_pos, y + bg.sz().y);
                    x_pos = Math.max(x_pos, x + bg.sz().x);
                }
            }
            for (int x = ctl.sz().x / 2; x + bg.sz().x < sz.x - ctl.sz().x / 2; x += bg.sz().x)
            {
                g.image(bg, new Coord(x, y_pos), new Coord(bg.sz().x, sz.y - y_pos - ctl.sz().y / 2));
                x_pos = Math.max(x_pos, x + bg.sz().x);
            }
            for (int y = ctl.sz().y / 2; y + bg.sz().y < sz.y - ctl.sz().y / 2; y += bg.sz().y)
            {
                g.image(bg, new Coord(x_pos, y), new Coord(sz.x - x_pos - ctl.sz().x / 2, bg.sz().y));
                y_pos = Math.max(y_pos, y + bg.sz().y);
            }
            if (x_pos < sz.x - ctl.sz().x / 2 && y_pos < sz.y - ctl.sz().y / 2)
            {
                g.image(bg, new Coord(x_pos, y_pos), new Coord(sz.x - x_pos - ctl.sz().x / 2, sz.y - y_pos - ctl.sz().y / 2));
            }
        }
    }

    /**
     * Forward a click to one of the control buttons, translating the event into
     * the button's own coordinate space. The buttons sit visually on top of the
     * content while in DRAG mode, but the content is higher in the event z-order
     * (it is added last), so it would otherwise swallow clicks aimed at the
     * buttons. Handling them explicitly here makes lock/visibility/flip work on
     * every window regardless of what the content does with the event.
     */
    private boolean btnClick(ICheckBox btn, MouseDownEvent ev) {
        return btn.visible() && btn.mousedown(ev.derive(ev.c.sub(btn.c)));
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if (ui.core.mode == NCore.Mode.DRAG) {
            if (btnClick(btnLock, ev) || btnClick(btnVis, ev) || btnClick(btnFlip, ev))
                return true;

            if (ev.c.isect(Coord.z, sz)) {
                // Start dragging only when this widget is unlocked, nothing else
                // is currently grabbed and it is the left mouse button.
                if (ev.b == 1 && !btnLock.a && ui.grabs.isEmpty()) {
                    startmove(ev);
                }
                // Consume the event so it does not fall through to widgets
                // stacked underneath this one. Without this, overlapping
                // draggable widgets would all grab the mouse at once and get
                // stuck to the cursor on release. Only the topmost widget under
                // the pointer should react.
                return true;
            }
        } else if (adjustclick(ev) && ev.c.isect(Coord.z, sz) && !btnLock.a && ui.grabs.isEmpty()) {
            /* Mid-game grab. Consumed, so the modifier also shields the content from the
             * click that starts the move. */
            startmove(ev);
            return true;
        }
        if (scaled()) {
            ev.stop();
            Coord cc = tocontent(ev.c);
            return(content.visible() && cc.isect(Coord.z, content.sz) && ev.derive(cc).dispatch(content));
        }
        return super.mousedown(ev);
    }

    @Override
    public boolean mousewheel(MouseWheelEvent ev) {
        if(!btnLock.a && ev.c.isect(Coord.z, sz)) {
            /* A bare wheel still reaches the content underneath; only the layout chord
             * resizes the panel. */
            if(Hotkeys.action(Hotkeys.LAYOUT_SCALE_UP).current().matchesWheel(ev.a, ui.modflags())) {
                scale(scale() + 0.05);
                return(true);
            }
            if(Hotkeys.action(Hotkeys.LAYOUT_SCALE_DOWN).current().matchesWheel(ev.a, ui.modflags())) {
                scale(scale() - 0.05);
                return(true);
            }
        }
        if(scaled()) {
            ev.stop();
            Coord cc = tocontent(ev.c);
            return(content.visible() && cc.isect(Coord.z, content.sz) && ev.derive(cc).dispatch(content));
        }
        return(super.mousewheel(ev));
    }

    @Override
    public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
        if(scaled()) {
            ev.stop();
            Coord cc = tocontent(ev.c);
            if(!content.visible() || !cc.isect(Coord.z, content.sz))
                return(false);
            return(ev.derive(cc).hovering(hovering).dispatch(content));
        }
        return(super.mousehover(ev, hovering));
    }

    @Override
    public boolean getcurs(CursorQuery ev) {
        if(!scaled())
            return(false);
        ev.stop();
        Coord cc = tocontent(ev.c);
        return(content.visible() && cc.isect(Coord.z, content.sz) && ev.derive(cc).dispatch(content));
    }

    @Override
    public boolean tooltip(TooltipQuery ev) {
        if(scaled()) {
            ev.stop();
            Coord cc = tocontent(ev.c);
            return(content.visible() && cc.isect(Coord.z, content.sz) && ev.derive(cc).dispatch(content));
        }
        return(super.tooltip(ev));
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if (dm != null)
        {
            target_c.x = this.c.x;
            target_c.y = this.c.y;
            persist();
            dm.remove();
            dm = null;
            return true;
        }
        else if (scaled())
        {
            ev.stop();
            Coord cc = tocontent(ev.c);
            return(content.visible() && cc.isect(Coord.z, content.sz) && ev.derive(cc).dispatch(content));
        }
        else
        {
            return super.mouseup(ev);
        }
    }


    @Override
    public void mousemove(MouseMoveEvent ev) {
        if (dm != null || ui.core.mode == NCore.Mode.DRAG)
        {

            if (dm != null)
            {
                Coord prepc = this.c.add(ev.c.add(doff.inv()));
                Coord newc = prepc.div(UI.scale(8)).mul(UI.scale(8)).sub(UI.scale(4),UI.scale(4));
                
                // Snap to screen edges
                if(NUtils.getGameUI() != null && NUtils.getGameUI().sz != Coord.z) {
                    int snapThreshold = UI.scale(20); // Distance at which snapping activates
                    Coord screenSz = NUtils.getGameUI().sz;
                    
                    // Snap to left edge
                    if(newc.x < snapThreshold) {
                        newc.x = 0;
                    }
                    // Snap to top edge
                    if(newc.y < snapThreshold) {
                        newc.y = 0;
                    }
                    // Snap to right edge
                    if(newc.x + sz.x > screenSz.x - snapThreshold) {
                        newc.x = screenSz.x - sz.x;
                    }
                    // Snap to bottom edge
                    if(newc.y + sz.y > screenSz.y - snapThreshold) {
                        newc.y = screenSz.y - sz.y;
                    }
                }

                snapNeighbours(newc);
                this.c = newc;
            }
            else
            {
                if (ev.c.isect(Coord.z, sz))
                {
                    btnLock.mousemove(ev);
                    btnVis.mousemove(ev);
                    if(isFlipped)
                        btnFlip.mousemove(ev);
                }
            }
        }
        else if (scaled())
        {
            ev.stop();
            Coord cc = tocontent(ev.c);
            if(content.visible())
                ev.derive(cc).dispatch(content);
        }
        else
        {
            super.mousemove(ev);
        }

    }

    @Override
    public void tick(double dt)
    {
        super.tick(dt);
        if((current == null) && wantslider())
        {
            /* Beside the cursor, like a tooltip, and kept on screen. */
            ScaleSlider s = new ScaleSlider(this);
            Coord pos = parent.rootxlate(ui.mc).add(UI.scale(16), UI.scale(16));
            if(parent.sz != Coord.z)
            {
                pos.x = Math.max(0, Math.min(pos.x, parent.sz.x - s.sz.x));
                pos.y = Math.max(0, Math.min(pos.y, parent.sz.y - s.sz.y));
            }
            current = s;
            parent.add(s, pos).z(1000);
        }
        if (ui.core.mode == NCore.Mode.DRAG)
        {
            btnLock.show();
            btnVis.show();
            if( isFlipped )
                btnFlip.show();
        }
        else
        {
            if (btnLock.visible())
            {
                btnLock.hide();
                btnVis.hide();
                btnFlip.hide();
            }
        }

        if (ui.core.mode == NCore.Mode.DRAG)
        {
            colliding = false;
            if(parent != null && sz != Coord.z && visible())
            {
                for(Widget wdg : parent.children())
                {
                    if((wdg instanceof NDraggableWidget) && (wdg != this) && wdg.visible()
                       && (wdg.sz != Coord.z) && isect((NDraggableWidget)wdg))
                    {
                        colliding = true;
                        break;
                    }
                }
            }
        }
        else if(colliding)
        {
            colliding = false;
        }

        if(usingDefault && NUtils.getGameUI()!=null && NUtils.getGameUI().sz!=Coord.z && sz!=Coord.z && dm == null)
        {
            Coord def = NDefaultLayout.resolve(name, sz, NUtils.getGameUI().sz);
            target_c.x = def.x;
            target_c.y = def.y;
        }

        if(NUtils.getGameUI()!=null && NUtils.getGameUI().sz!=Coord.z && dm == null)
        {
            if (c.x + sz.x > NUtils.getGameUI().sz.x - GameUI.margin.x)
                c.x = NUtils.getGameUI().sz.x - sz.x;
            else
                c.x = target_c.x;
            if (c.y + sz.y > NUtils.getGameUI().sz.y - GameUI.margin.y)
                c.y = NUtils.getGameUI().sz.y - sz.y;
            else
                c.y = target_c.y;
        }
    }

    public String getName()
    {
        return name;
    }

    public void flipContent()
    {
        content.flip(btnFlip.a);
        resize(content.sz.add(contentDelta()));
    }

    public void setFlipped(boolean val)
    {
        isFlipped = val;
        flipContent();
    }
}
