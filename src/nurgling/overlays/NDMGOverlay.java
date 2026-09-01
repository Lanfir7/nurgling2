package nurgling.overlays;

import haven.*;
import haven.render.Homo3D;
import haven.render.Pipe;
import nurgling.NUtils;
import nurgling.tools.CreatureHp;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.WeakHashMap;

public class NDMGOverlay extends Sprite implements PView.Render2D {

    /**
     * Clears NDMGOverlay overlays on the current session's gobs and forgets
     * that session's fight-scoped totals.
     */
    public static void clearAll() {
        synchronized (live) {
            live.clear();
        }
        if (NUtils.getGameUI() == null || NUtils.getGameUI().ui == null ||
            NUtils.getGameUI().ui.sess == null || NUtils.getGameUI().ui.sess.glob == null) {
            NCombatDamageStore.clearAll();
            return;
        }
        NCombatDamageStore.clearSession(NUtils.getGameUI().ui.sess.glob);
        synchronized (NUtils.getGameUI().ui.sess.glob.oc) {
            for (Gob gob : NUtils.getGameUI().ui.sess.glob.oc) {
                Gob.Overlay ol = gob.findol(NDMGOverlay.class);
                if (ol != null) {
                    ol.remove();
                }
            }
        }
    }

    /** Drop stored totals for one gob when its fight relation ends. */
    public static void forgetGob(Object session, long gobid) {
        NCombatDamageStore.clear(session, gobid);
    }

    /* Gob.addcustomol() defers the actual add to a loader thread, so findol() cannot
     * see a freshly created overlay yet. A single hit sends its hard- and soft-health
     * scores together and both land inside that window, so without a registry of
     * in-flight overlays each score would build its own sprite and the two would draw
     * on top of each other at the same point above the gob. Weak keys so a gob leaving
     * view takes its entry with it. */
    private static final Map<Gob, NDMGOverlay> live = new WeakHashMap<>();
    public static final Text.Foundry fnd = new Text.Foundry(Text.sans, 12);
    Color[] colt = new Color[]{Color.RED, Color.YELLOW, Color.GREEN};
    TexI[] dmgt = new TexI[3];
    int[] dmg = new int[3];

    public NDMGOverlay(Owner owner) {
        super(owner, null);
        seedFromStore();
    }

    private void seedFromStore() {
        Gob gob = ownerGob();
        if(gob == null)
            return;
        NCombatDamageStore.copyInto(gob.glob, gob.id, dmg);
    }

    private Gob ownerGob() {
        return (owner instanceof Gob) ? (Gob) owner : null;
    }

    public static void IsDMG(Message sdt, Gob g) {
        if (sdt.rt == 7) {
            MessageBuf buf = new MessageBuf(sdt);
            int dmg = buf.int32();
            buf.uint8();
            int type = buf.uint16();

        }
    }

    public static void IsDMG(int col, int num, Gob owner) {
        int type;
        if (col == 64527) {
            type = 1;
        } else if (col == 36751) {
            type = 2;
        } else if (col == 61455) {
            type = 0;
        } else {
            return;
        }
        NDMGOverlay ol;
        synchronized (live) {
            Gob.Overlay gol = owner.findol(NDMGOverlay.class);
            ol = (gol != null) ? (NDMGOverlay) gol.spr : live.get(owner);
            if (ol == null) {
                ol = new NDMGOverlay(owner);
                live.put(owner, ol);
                owner.addcustomol(ol);
            }
        }
        ol.updDmg(num, type);
    }

    public int total() {
        return CreatureHp.hpDealt(dmg[0], dmg[1], dmg[2]);
    }

    public synchronized void updDmg(int dmg, int type) {
        this.dmg[type] += dmg;
        persist();
        rebuildTextures();
    }

    private void persist() {
        Gob gob = ownerGob();
        if(gob != null)
            NCombatDamageStore.replace(gob.glob, gob.id, this.dmg[0], this.dmg[1], this.dmg[2]);
    }

    private void rebuildTextures() {
        for(int i = 0; i < 3; i++) {
            if(this.dmg[i] != 0)
                dmgt[i] = new TexI(Utils.outline2(fnd.render(Integer.toString(this.dmg[i]), colt[i]).img, Utils.contrast(colt[i])));
            else
                dmgt[i] = null;
        }
        int w = 0;
        int h = 0;
        for(int i = 0; i < 3; i++) {
            if (dmgt[i] != null) {
                w += dmgt[i].sz().x + UI.scale(2);
                h = Math.max(h, dmgt[i].sz().y + UI.scale(2));
            }
        }
        if((w <= 0) || (h <= 0)) {
            pending = null;
            return;
        }
        BufferedImage ret = TexI.mkbuf(new Coord(w, h));
        Graphics g = ret.getGraphics();
        Coord pos = new Coord(0, 0);
        for(int i = 0; i < 3; i++) {
            if(dmgt[i] != null) {
                g.drawImage(dmgt[i].back, pos.x, pos.y, null);
                pos.x += dmgt[i].sz().x + UI.scale(2);
            }
        }
        g.dispose();
        pending = ret;
    }

    /* Set on the thread that parses the score message, picked up and uploaded once by
     * the render thread. Converting on every frame instead meant a fresh texture upload
     * per damaged gob per frame. */
    private volatile BufferedImage pending = null;
    private TexI curOl = null;

    /* Dark enough to read a thin stroked digit against grass, snow or firelight. */
    private static final Color backing = new Color(0, 0, 0, 187);
    private static final Coord bpad = UI.scale(new Coord(3, 1));

    public void draw(GOut g, Pipe state) {
        BufferedImage upd = pending;
        if(upd != null) {
            pending = null;
            if(curOl != null)
                curOl.dispose();
            curOl = new TexI(upd);
        }
        if(curOl == null)
            return;
        Coord sc = Homo3D.obj2view(Coord3f.zu.add(0, 0, 16), state, Area.sized(Coord.z, g.sz())).round2();
        if(sc == null)
            return;
        sc = sc.add(0, curOl.sz().y * 2);
        Coord sz = curOl.sz();
        Coord ul = sc.sub(sz.div(2));
        g.chcolor(backing);
        g.frect2(ul.sub(bpad), ul.add(sz).add(bpad));
        g.chcolor();
        g.image(curOl, ul);
    }

    @Override
    public boolean tick(double dt) {
        return super.tick(dt);
    }
}
