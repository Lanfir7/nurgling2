package nurgling.widgets;

import haven.*;
import haven.render.ColorTex;
import haven.render.Texture;

import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Proximity reveal for a row of small HUD icons. The icons are invisible while the cursor
 * is elsewhere, fade in as it comes closer, and the one nearest the cursor grows while its
 * neighbours grow progressively less. That keeps a tiny icon easy to aim at without it
 * occupying the panel it sits on for the rest of the time.
 */
public class NIconDock {
    /** Cursor distance from the panel at which the icons are fully revealed. */
    static final double FADE = UI.scale(70);
    /** Cursor distance from an icon beyond which it keeps its plain size. */
    static final double REACH = UI.scale(55);
    /** How much the icon right under the cursor grows. */
    static final double GROW = 0.9;
    /** Opacity of an icon that is revealed but far from the cursor. */
    static final double DIM = 0.4;

    private double show = 0;
    private Coord mc = null;

    /**
     * Advance the reveal towards where the cursor is now. `mc` is the cursor in
     * panel-local coordinates, or null when there is none to report.
     */
    public void track(Coord mc, Coord sz, double dt) {
        this.mc = mc;
        double want = ((mc == null) || (sz == null)) ? 0 : reveal(rectdist(mc, sz));
        /* Eased, so a pointer crossing the edge does not make the icons blink. */
        show += (want - show) * Math.min(1.0, dt * 12.0);
    }

    /** True while the icons are faint enough that drawing them is pointless. */
    public boolean hidden() {
        return(show < 0.02);
    }

    /** Size multiplier for an icon centred at `mid`. */
    public double scale(Coord mid) {
        return((mc == null) ? 1.0 : scale(mc.dist(mid)));
    }

    /** Opacity of an icon at the given size multiplier, 0 to 255. */
    public int alpha(double scale) {
        double lit = (scale - 1.0) / GROW;
        return((int)Math.round(255 * show * (DIM + ((1.0 - DIM) * lit))));
    }

    /** 0 when the cursor is far enough to hide the icons, 1 when it is over the panel. */
    static double reveal(double dist) {
        return(Utils.clip(1.0 - (dist / FADE), 0.0, 1.0));
    }

    /** Size multiplier for an icon whose centre is `dist` away from the cursor. */
    static double scale(double dist) {
        double f = Utils.clip(1.0 - (dist / REACH), 0.0, 1.0);
        /* Smoothstepped, so the row reads as a staircase around the cursor rather than
         * one long linear ramp. */
        return(1.0 + (GROW * f * f * (3.0 - (2.0 * f))));
    }

    /** Shortest distance from `c` to a rectangle at the origin, 0 when inside it. */
    static double rectdist(Coord c, Coord sz) {
        int dx = Math.max(0, Math.max(-c.x, c.x - sz.x));
        int dy = Math.max(0, Math.max(-c.y, c.y - sz.y));
        return(Math.sqrt((dx * dx) + (dy * dy)));
    }

    /**
     * Top-left of a grown icon. Overlay buttons along the bottom grow up and right
     * from their left edge so they stay inside the frame; the map button in the
     * top-right grows down and left.
     */
    static Coord grownUl(Coord mid, Coord orig, Coord grown, boolean growLeft, boolean growDown) {
        int x = growLeft ? mid.x + (orig.x / 2) - grown.x : mid.x - (orig.x / 2);
        int y = growDown ? mid.y - (orig.y / 2) : mid.y + (orig.y / 2) - grown.y;
        return(new Coord(x, y));
    }

    /** up, down, hoverup, hoverdown. Bright/color art is ON when `colorWhenOn`. */
    static String[] overlayArt(String p, boolean colorWhenOn) {
        return colorWhenOn
            ? new String[] {p + "d", p + "u", p + "dh", p + "h"}
            : new String[] {p + "u", p + "d", p + "h", p + "dh"};
    }

    /** How many times larger the paint texture is than the button's layout size. */
    static final int HIRES = 4;

    /**
     * Lanczos 4× of `src` for hover. `logical` is the on-screen button size; if the
     * file is already at least that large, it is kept so a 2× asset is not downscaled
     * before being grown.
     */
    static BufferedImage hiRes(BufferedImage src, Coord logical) {
        if((src == null) || (logical == null) || (logical.x <= 0) || (logical.y <= 0))
            return(src);
        Coord want = logical.mul(HIRES);
        Coord have = PUtils.imgsz(src);
        if((have.x >= want.x) && (have.y >= want.y))
            return(src);
        return(PUtils.uiscale(PUtils.coercergba(src), want));
    }

    static Tex hiTex(Resource.Image img) {
        if(img == null)
            return(null);
        return(hiTex(img.img, img.ssz));
    }

    static Tex hiTex(BufferedImage src, Coord logical) {
        BufferedImage hi = hiRes(src, logical);
        if(hi == null)
            return(null);
        /* LINEAR is applied on first draw, so construction does not need a GL context. */
        return(new TexI(hi) {
            @Override
            public ColorTex st() {
                ColorTex st = super.st();
                st.data.magfilter(Texture.Filter.LINEAR).minfilter(Texture.Filter.LINEAR);
                return(st);
            }
        });
    }

    private final Map<Tex, Tex> hiPaint = new IdentityHashMap<>();

    /** Cached 4× paint texture for a dock icon that only has the layout-sized Tex. */
    Tex paint(Tex src) {
        if(src == null)
            return(null);
        Tex hi = hiPaint.get(src);
        if(hi != null)
            return(hi);
        if(src instanceof TexI) {
            TexI t = (TexI)src;
            hi = hiTex(t.back, t.sz());
        } else {
            hi = src;
        }
        hiPaint.put(src, hi);
        return(hi);
    }
}
