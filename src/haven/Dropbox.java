package haven;

import java.awt.Color;

public abstract class Dropbox<T> extends ListWidget<T> {
    public static final Tex drop = Resource.loadtex("gfx/hud/drop");
    public final int listh;
    private final Coord dropc;
    private Droplist dl;

    public Dropbox(int w, int listh, int itemh) {
        super(new Coord(w, itemh), itemh);
        this.listh = listh;
        dropc = new Coord(sz.x - drop.sz().x, 0);
    }

    private class Droplist extends Listbox<T> {
        private UI.Grab grab = null;
        private int hover = -1;

        private Droplist() {
            super(Dropbox.this.sz.x, Math.min(listh, Dropbox.this.listitems()), Dropbox.this.itemh);
            sel = Dropbox.this.sel;
            Dropbox.this.ui.root.add(this, Dropbox.this.rootpos().add(0, Dropbox.this.sz.y));
            z(1000);
            raise();
            grab = ui.grabmouse(this);
            display();
        }

        protected T listitem(int i) {return(Dropbox.this.listitem(i));}
        protected int listitems() {return(Dropbox.this.listitems());}

        protected void drawbg(GOut g) {
            g.chcolor(DropboxVisualStyle.popupBackground());
            g.frect(Coord.z, sz);
            g.chcolor();
        }

        protected void drawsel(GOut g) {
            g.chcolor(DropboxVisualStyle.selectedBackground());
            g.frect(Coord.z, g.sz());
            g.chcolor();
        }

        protected void drawitem(GOut g, T item, int idx) {
            if((idx == hover) && (item != sel)) {
                g.chcolor(DropboxVisualStyle.hoverBackground());
                g.frect(Coord.z, g.sz());
                g.chcolor();
            }
            Dropbox.this.drawitem(g, item, idx);
        }

        public void draw(GOut g) {
            super.draw(g);
            drawBorder(g, true);
        }

        public void mousemove(MouseMoveEvent ev) {
            int idx = idxat(ev.c);
            hover = (ev.c.isect(Coord.z, sz) && (idx >= 0) &&
                    (idx < listitems())) ? idx : -1;
            super.mousemove(ev);
        }

        public void destroy() {
            grab.remove();
            super.destroy();
            dl = null;
        }

        public void change(T item) {
            Dropbox.this.change(item);
            reqdestroy();
        }
    }

    public static Color bgColor = DropboxVisualStyle.fieldBackground();

    private static void drawBorder(GOut g, boolean open) {
        int thickness = Math.max(1, UI.scale(1));
        g.chcolor(DropboxVisualStyle.border(open));
        for(DropboxVisualStyle.BorderFrame frame :
                DropboxVisualStyle.borderFrames(g.sz(), thickness))
            g.rect(frame.position, frame.size);
        g.chcolor();
    }

    public void draw(GOut g) {
        g.chcolor(bgColor);
        g.frect(Coord.z, sz);
        g.chcolor(DropboxVisualStyle.arrowBackground());
        g.frect(dropc, new Coord(drop.sz().x, sz.y));
        g.chcolor();
        if(sel != null)
            drawitem(g.reclip(Coord.z, new Coord(sz.x - drop.sz().x, itemh)), sel, 0);
        g.image(drop, dropc);
        drawBorder(g, dl != null);
        super.draw(g);
    }

    public boolean mousedown(MouseDownEvent ev) {
        if(super.mousedown(ev))
            return(true);
        if((dl == null) && (ev.b == 1)) {
            dl = new Droplist();
            return(true);
        }
        return(true);
    }
}
