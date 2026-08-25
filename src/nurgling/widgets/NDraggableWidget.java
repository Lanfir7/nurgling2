package nurgling.widgets;

import haven.*;
import haven.Window;
import nurgling.*;
import nurgling.conf.*;

import java.awt.*;

import static nurgling.widgets.NCatSelection.fnd;

public class NDraggableWidget extends Widget
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
    public Widget content = null;
    TexI label = null;
    public static Text.Furnace fnd = new PUtils.BlurFurn(new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 14, Color.YELLOW).aa(true), UI.scale(1), UI.scale(2), Color.BLACK);
    public NDraggableWidget(Widget content, String name, Coord sz)
    {
        this(name,sz);
        this.content = add(content);
        this.content.visible = btnVis.a;
        content.resize(this.sz.sub(delta));
        content.move(off);
    }

    public NDraggableWidget(String name, Coord sz)
    {
        label = new TexI(fnd.render(NDefaultLayout.title(name)).img);
        this.sz = sz;
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
        if((ui.core.mode == NCore.Mode.DRAG) && (ev.code == java.awt.event.KeyEvent.VK_Z)
           && ((ui.modflags() & UI.MOD_CTRL) != 0)) {
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
        NDragProp.set(name, prop);
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
    }

    @Override
    public void resize(Coord sz)
    {
        super.resize(sz);
        btnLock.move(new Coord(sz.x - NStyle.locki[0].sz().x - NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y / 2));
        btnVis.move(new Coord(sz.x - NStyle.locki[0].sz().x - NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y + off.y));
        if(isFlipped)
            btnFlip.move(new Coord(NStyle.locki[0].sz().x / 2, NStyle.locki[0].sz().y/2));
        if(content!=null)
        {
            content.resize(sz.sub(delta));
            content.move(off);
        }
    }

    public static final Tex bg = Resource.loadtex("nurgling/hud/wnd/bg");
    private static final Tex ctl = Resource.loadtex("nurgling/hud/box/tl");

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
            g.aimage(label, sz.div(2), 0.5, 0.5);
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
                    pushUndo();
                    dm = ui.grabmouse(this);
                    doff = ev.c;
                    parent.setfocus(this);
                }
                // Consume the event so it does not fall through to widgets
                // stacked underneath this one. Without this, overlapping
                // draggable widgets would all grab the mouse at once and get
                // stuck to the cursor on release. Only the topmost widget under
                // the pointer should react.
                return true;
            }
        }
        return super.mousedown(ev);
    }


    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if (dm != null && ui.core.mode == NCore.Mode.DRAG)
        {
            target_c.x = this.c.x;
            target_c.y = this.c.y;
            persist();
            dm.remove();
            dm = null;
            return true;
        }
        else
        {
            return super.mouseup(ev);
        }
    }


    @Override
    public void mousemove(MouseMoveEvent ev) {
        if (ui.core.mode == NCore.Mode.DRAG)
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
        else
        {
            super.mousemove(ev);
        }

    }

    @Override
    public void tick(double dt)
    {
        super.tick(dt);
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
        resize(content.sz.add(delta));
    }

    public void setFlipped(boolean val)
    {
        isFlipped = val;
        flipContent();
    }
}
