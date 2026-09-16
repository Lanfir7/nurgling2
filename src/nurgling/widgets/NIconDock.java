package nurgling.widgets;

import haven.*;

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
}
