package nurgling.sessions;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUI;

/**
 * Small 3D portrait of a session's character.
 *
 * The widget lives in the currently rendered UI, so it cannot look the character up by
 * gob id the way a plain Avaview does. Instead it periodically snapshots the composite
 * description of that session's player and feeds it in through {@link Avaview#pop}, which
 * resolves resources through the owning session.
 */
public class SessionAvatar extends Avaview {
    private static final double REFRESH_INTERVAL = 0.5;

    private final SessionContext ctx;
    private java.util.List<Composited.MD> lastmod;
    private java.util.List<Composited.ED> lastequ;
    private double sincerefresh = REFRESH_INTERVAL;

    public SessionAvatar(Coord sz, SessionContext ctx) {
        super(sz, -1, "avacam");
        this.ctx = ctx;
        this.clearcolor = new FColor(0.055f, 0.066f, 0.062f);
    }

    @Override
    public void draw(GOut g) {
        // The portrait is cosmetic; a session tearing down must never take the HUD with it.
        try {
            super.draw(g);
        } catch(Exception e) {
        }
    }

    @Override
    public void tick(double dt) {
        if((sincerefresh += dt) >= REFRESH_INTERVAL) {
            sincerefresh = 0;
            refresh();
        }
        try {
            super.tick(dt);
        } catch(Exception e) {
        }
    }

    private void refresh() {
        try {
            NUI sui = ctx.ui;
            NGameUI gui = ctx.getGameUI();
            if((sui == null) || (sui.sess == null) || (gui == null))
                return;
            Gob pl = sui.sess.glob.oc.getgob(gui.plid);
            if(pl == null)
                return;
            Drawable d = pl.getattr(Drawable.class);
            if(!(d instanceof Composite))
                return;
            Composite gc = (Composite)d;
            if(gc.comp == null)
                return;
            if((gc.comp.cmod == lastmod) && (gc.comp.cequ == lastequ) && (avadesc != null))
                return;
            lastmod = gc.comp.cmod;
            lastequ = gc.comp.cequ;
            Composited.Desc desc = new Composited.Desc(gc.base);
            for(Composited.MD md : lastmod)
                desc.mod.add(md.clone());
            for(Composited.ED ed : lastequ)
                desc.equ.add(ed.clone());
            pop(desc, sui.sess);
        } catch(Loading e) {
        } catch(Exception e) {
        }
    }
}
