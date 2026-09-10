/* Preprocessed source code */
package haven.res.ui.inspect;

import haven.*;
import java.util.*;
import nurgling.NUtils;

/* >wdg: LocalInspect */
@FromResource(name = "ui/inspect", version = 5, override = true)
public class LocalInspect extends Widget {
    public MapView mv;
    public Hover last = null, cur = null;

    public static Widget mkwidget(UI ui, Object... args) {
        return(new LocalInspect());
    }

    protected void added() {
        super.added();
        mv = getparent(GameUI.class).map;
        move(Coord.z);
        resize(parent.sz);
    }

    public class Hover extends MapView.Hittest {
        public volatile boolean done = false;
        public Coord2d mc;
        public ClickData inf;
        public Gob ob;
        public Object tip;

        public Hover(Coord c) {
            mv.super(c);
        }

        protected void hit(Coord pc, Coord2d mc, ClickData inf) {
            this.mc = mc;
            this.inf = inf;
            this.done = true;
            if(inf != null) {
                for(Object o : inf.array()) {
                    if(o instanceof Gob) {
                        ob = (Gob)o;
                        break;
                    }
                }
            }
        }

        protected void nohit(Coord pc) {
            done = true;
        }

        public Object tip() {
            if(ob != null) {
                SavedInfo info = ob.getattr(SavedInfo.class);
                return(new ObTip(gobname(ob), (info == null) ? Collections.emptyList() : info.lines));
            }
            if(mc != null) {
                int tid = ui.sess.glob.map.gettile(mc.floor(MCache.tilesz));
                Resource tile = ui.sess.glob.map.tileset(tid).getres();
                Resource.Tooltip name = tile.layer(Resource.tooltip);
                if(name != null)
                    return(name.t);
            }
            return(null);
        }
    }

    static String gobname(Gob gob) {
        GobIcon icon = gob.getattr(GobIcon.class);
        if(icon != null) {
            Resource.Tooltip name = icon.res.get().layer(Resource.tooltip);
            if(name != null)
                return(name.t);
        }
        Drawable drawable = gob.getattr(Drawable.class);
        if(drawable != null) {
            Resource resource = drawable.getres();
            if(resource != null) {
                Resource.Tooltip name = resource.layer(Resource.tooltip);
                if(name != null)
                    return(name.t);
                return(NUtils.prettyResName(resource.name).toString());
            }
        }
        return(null);
    }

    public static class ObTip implements Indir<Tex> {
        public final String name;
        public final List<String> lines;
        private Tex tex;
        private boolean r = false;

        public ObTip(String name, List<String> lines) {
            this.name = name;
            this.lines = lines;
        }

        public boolean equals(ObTip that) {
            return(Utils.eq(this.name, that.name) && Utils.eq(this.lines, that.lines));
        }

        public boolean equals(Object that) {
            return((that instanceof ObTip) && equals((ObTip)that));
        }

        public Tex get() {
            if(!r) {
                StringBuilder buf = new StringBuilder();
                if(name != null)
                    buf.append(RichText.Parser.quote(name));
                if(!lines.isEmpty()) {
                    if(buf.length() > 0)
                        buf.append("\n\n");
                    for(String line : lines)
                        buf.append(RichText.Parser.quote(line) + "\n");
                }
                if(buf.length() > 0)
                    tex = new TexI(RichText.render(buf.toString(), 0).img);
                r = true;
            }
            return(tex);
        }
    }

    public boolean active() {
        return(true);
    }

    public void tick(double dt) {
        super.tick(dt);
        if((cur != null) && cur.done) {
            last = cur;
            cur = null;
        }
        if(active()) {
            if(cur == null) {
                Coord mvc = mv.rootxlate(ui.mc);
                if(mv.area().contains(mvc))
                    (cur = new Hover(mvc)).run();
            }
        } else {
            last = null;
        }
    }

    public Object tooltip(Coord c, Widget prev) {
        if(active() && (last != null))
            return(last.tip());
        return(super.tooltip(c, prev));
    }
}
