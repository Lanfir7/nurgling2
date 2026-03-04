package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.RichText;
import haven.Text;
import haven.Tex;
import haven.UI;
import haven.Widget;
import haven.Resource;

import java.awt.Color;
import java.awt.font.TextAttribute;
import java.util.LinkedList;
import java.util.List;

public class AgentTextlog extends Widget {
    private static final Tex TEXPAP = Resource.loadtex("gfx/hud/texpap");
    private static final Tex SCHAIN = Resource.loadtex("gfx/hud/schain");
    private static final Tex SFLARP = Resource.loadtex("gfx/hud/sflarp");
    private static final RichText.Foundry FND = new RichText.Foundry(
            TextAttribute.FAMILY, "SansSerif",
            TextAttribute.SIZE, UI.scale(12f),
            TextAttribute.FOREGROUND, Color.BLACK);

    private final List<Text> lines = new LinkedList<>();
    private final int margin = UI.scale(4);
    private int maxy = 0;
    private int cury = 0;
    private UI.Grab sdrag = null;

    public AgentTextlog(Coord sz) {
        super(sz);
    }

    @Override
    public void draw(GOut g) {
        Coord dc = new Coord();
        for (dc.y = 0; dc.y < sz.y; dc.y += TEXPAP.sz().y) {
            for (dc.x = 0; dc.x < sz.x; dc.x += TEXPAP.sz().x) {
                g.image(TEXPAP, dc);
            }
        }
        g.chcolor();
        int y = -cury;
        synchronized (lines) {
            for (Text line : lines) {
                int dy1 = sz.y + y;
                int dy2 = dy1 + line.sz().y;
                if ((dy2 > 0) && (dy1 < sz.y)) {
                    g.image(line.tex(), new Coord(margin, dy1));
                }
                y += line.sz().y;
            }
        }
        if (maxy > sz.y) {
            int fx = sz.x - SFLARP.sz().x;
            int cx = fx + (SFLARP.sz().x / 2) - (SCHAIN.sz().x / 2);
            for (y = 0; y < sz.y; y += SCHAIN.sz().y - 1) {
                g.image(SCHAIN, new Coord(cx, y));
            }
            double a = (double) (cury - sz.y) / (double) (maxy - sz.y);
            int fy = (int) ((sz.y - SFLARP.sz().y) * a);
            g.image(SFLARP, new Coord(fx, fy));
        }
    }

    public void append(String line) {
        Text rl = FND.render(decorateLine(line), sz.x - (margin * 2) - SFLARP.sz().x);
        synchronized (lines) {
            lines.add(rl);
        }
        if (cury == maxy) {
            cury += rl.sz().y;
        }
        maxy += rl.sz().y;
    }

    @Override
    public void resize(Coord sz) {
        super.resize(sz);
        int h = 0;
        synchronized (lines) {
            for (Text line : lines) {
                h += line.sz().y;
            }
        }
        maxy = h;
        if (cury < this.sz.y) {
            cury = this.sz.y;
        }
        if (cury > maxy) {
            cury = maxy;
        }
    }

    private static String decorateLine(String line) {
        if (line == null) return "";
        if (line.startsWith("ASSISTANT:")) {
            String msg = line.substring("ASSISTANT:".length()).trim();
            return "$b{$col[120,190,255]{ASSISTANT:}} " + RichText.Parser.quote(msg);
        }
        if (line.startsWith("YOU:")) {
            String msg = line.substring("YOU:".length()).trim();
            return "$b{$col[128,255,128]{YOU:}} " + RichText.Parser.quote(msg);
        }
        if (line.startsWith("TOOL ")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String who = line.substring(0, idx + 1).trim();
                String msg = line.substring(idx + 1).trim();
                return "$b{$col[255,200,120]{" + RichText.Parser.quote(who) + "}} " + RichText.Parser.quote(msg);
            }
            return "$b{$col[255,200,120]{TOOL}} " + RichText.Parser.quote(line.substring("TOOL ".length()).trim());
        }
        return RichText.Parser.quote(line);
    }

    @Override
    public boolean mousewheel(Widget.MouseWheelEvent ev) {
        cury += ev.a * UI.scale(20);
        if (cury < sz.y) cury = sz.y;
        if (cury > maxy) cury = maxy;
        return true;
    }

    private void updateScroll(Coord c) {
        double a = (double) (c.y - (SFLARP.sz().y / 2)) / (double) (sz.y - SFLARP.sz().y);
        if (a < 0) a = 0;
        if (a > 1) a = 1;
        cury = (int) (a * (maxy - sz.y)) + sz.y;
    }

    @Override
    public boolean mousedown(Widget.MouseDownEvent ev) {
        if (ev.b != 1) return super.mousedown(ev);
        int fx = sz.x - SFLARP.sz().x;
        if ((maxy > sz.y) && (ev.c.x >= fx)) {
            sdrag = ui.grabmouse(this);
            updateScroll(ev.c);
            return true;
        }
        return super.mousedown(ev);
    }

    @Override
    public void mousemove(Widget.MouseMoveEvent ev) {
        if (sdrag != null) {
            updateScroll(ev.c);
            return;
        }
        super.mousemove(ev);
    }

    @Override
    public boolean mouseup(Widget.MouseUpEvent ev) {
        if ((ev.b == 1) && (sdrag != null)) {
            sdrag.remove();
            sdrag = null;
            return true;
        }
        return super.mouseup(ev);
    }
}
