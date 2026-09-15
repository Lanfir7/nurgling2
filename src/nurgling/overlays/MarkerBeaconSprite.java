package nurgling.overlays;

import haven.Area;
import haven.Coord;
import haven.Coord3f;
import haven.GOut;
import haven.Gob;
import haven.HomoCoord4f;
import haven.Loading;
import haven.PView;
import haven.Sprite;
import haven.Tex;
import haven.Text;
import haven.UI;
import haven.render.Pipe;
import haven.render.RenderTree;
import haven.render.Homo3D;

import java.awt.Color;

/** A short-lived, world-anchored marker beacon. */
public class MarkerBeaconSprite extends Sprite implements PView.Render2D {
    public static final double LIFETIME = 20.0;
    private static final double HEIGHT = 110.0;
    private static final int RING_SEGMENTS = 28;
    private final Runnable finished;
    private final String labelText;
    private final Object labelLock = new Object();
    private double age;
    private boolean done;
    private Text label;
    private Text.Foundry labelFurnace;
    private int renderSlots;

    public MarkerBeaconSprite(Gob gob, Runnable finished) {
        this(gob, "", finished);
    }

    public MarkerBeaconSprite(Gob gob, String labelText, Runnable finished) {
        super(gob, null);
        this.finished = finished;
        this.labelText = labelText == null ? "" : labelText.trim();
    }

    @Override
    public boolean tick(double dt) {
        age += Math.max(0, dt);
        if(age < LIFETIME)
            return false;
        finish();
        return true;
    }

    /** Ends the beacon when it is evicted before its normal lifetime. */
    public void finish() {
        synchronized(labelLock) {
            if(done)
                return;
            done = true;
            releaseLabel();
        }
        finished.run();
    }

    @Override
    public void added(RenderTree.Slot slot) {
        synchronized(labelLock) {
            renderSlots++;
        }
    }

    @Override
    public void removed(RenderTree.Slot slot) {
        synchronized(labelLock) {
            if(renderSlots > 0)
                renderSlots--;
            if(renderSlots == 0)
                releaseLabel();
        }
    }

    @Override
    public void dispose() {
        finish();
        super.dispose();
    }

    @Override
    public void draw(GOut g, Pipe state) {
        try {
            drawBeacon(g, state);
        } catch(Loading ignored) {
            // The marker becomes visible as soon as its terrain page is available.
        }
    }

    private void drawBeacon(GOut g, Pipe state) {
        double fade = Math.min(1.0, (LIFETIME - age) / 2.0);
        if(fade <= 0)
            return;
        Area area = Area.sized(g.sz());
        Coord foot = project(state, area, 0, 0, 0.35);
        Coord head = project(state, area, 0, 0, HEIGHT);
        if(foot == null || head == null)
            return;

        beam(g, foot, head, fade);
        rings(g, state, area, fade);
        particles(g, state, area, fade);
        halo(g, head, fade);
        label(g, foot, fade);
        g.chcolor();
    }

    private void label(GOut g, Coord foot, double fade) {
        if(labelText.isEmpty())
            return;
        synchronized(labelLock) {
            if(done)
                return;
            if(label == null) {
                if(labelFurnace == null)
                    labelFurnace = new Text.Foundry(Text.dfont, 11).aa(true);
                label = labelFurnace.render(labelText, new Color(225, 245, 245));
            }
            Tex text = label.tex();
            Coord panel = text.sz().add(UI.scale(10), UI.scale(4));
            Coord center = foot.add(0, UI.scale(10));
            Coord ul = center.sub(panel.div(2));
            int alpha = (int)(215 * fade);
            g.chcolor(12, 16, 18, alpha);
            g.frect(ul, panel);
            g.chcolor(40, 215, 255, (int)(190 * fade));
            g.rect(ul, panel);
            g.chcolor(255, 255, 255, (int)(255 * fade));
            g.aimage(text, center, 0.5, 0.5);
        }
    }

    private void releaseLabel() {
        if(label != null) {
            label.dispose();
            label = null;
        }
    }

    private void beam(GOut g, Coord foot, Coord head, double fade) {
        int alpha = (int)(210 * fade);
        g.chcolor(5, 25, 40, (int)(alpha * 0.60));
        g.line(foot, head, 8);
        g.chcolor(40, 215, 255, alpha);
        g.line(foot, head, 3);
        g.chcolor(225, 255, 255, (int)(alpha * 0.78));
        g.line(foot, head, 1);
    }

    private void rings(GOut g, Pipe state, Area area, double fade) {
        for(int ring = 0; ring < 3; ring++) {
            double phase = ((age / 1.7) + (ring / 3.0)) % 1.0;
            double radius = 3 + (28 * (1 - Math.pow(1 - phase, 3)));
            int alpha = (int)(190 * fade * Math.pow(1 - phase, 1.6));
            if(alpha < 8)
                continue;
            Coord[] points = new Coord[RING_SEGMENTS + 1];
            for(int i = 0; i <= RING_SEGMENTS; i++) {
                double a = (Math.PI * 2 * i) / RING_SEGMENTS;
                points[i] = project(state, area, Math.cos(a) * radius, Math.sin(a) * radius, 0.42);
            }
            stroke(g, points, 5, new Color(4, 22, 34, (int)(alpha * 0.55)));
            stroke(g, points, 2, new Color(40, 215, 255, alpha));
        }
    }

    private void particles(GOut g, Pipe state, Area area, double fade) {
        for(int i = 0; i < 12; i++) {
            double cycle = ((age * 0.62) + (i * 0.137)) % 1.0;
            double angle = i * 2.399963229728653;
            double radius = 1.2 + ((i % 4) * 0.65) + (cycle * 2.4);
            Coord p = project(state, area, Math.cos(angle) * radius, Math.sin(angle) * radius,
                    2 + (cycle * (HEIGHT - 5)));
            if(p == null)
                continue;
            int alpha = (int)(220 * fade * Math.sin(Math.PI * cycle));
            if(alpha < 8)
                continue;
            int r = 1 + (int)(2 * (1 - cycle));
            g.chcolor(20, 125, 175, (int)(alpha * 0.50));
            g.fellipse(p, Coord.of(r + 2));
            g.chcolor(205, 250, 255, alpha);
            g.fellipse(p, Coord.of(r));
        }
    }

    private void halo(GOut g, Coord head, double fade) {
        double pulse = 1 + (0.18 * Math.sin(age * Math.PI * 2 / 1.7));
        int r = (int)(7 * pulse);
        int alpha = (int)(235 * fade);
        g.chcolor(4, 22, 34, (int)(alpha * 0.75));
        g.fellipse(head, Coord.of(r + 4));
        g.chcolor(45, 220, 255, alpha);
        g.fellipse(head, Coord.of(r));
        g.chcolor(240, 255, 255, (int)(alpha * 0.85));
        g.fellipse(head, Coord.of(Math.max(1, r - 4)));
    }

    private static void stroke(GOut g, Coord[] points, double width, Color color) {
        g.chcolor(color);
        for(int i = 1; i < points.length; i++) {
            if(points[i - 1] != null && points[i] != null)
                g.line(points[i - 1], points[i], width);
        }
    }

    private static Coord project(Pipe state, Area area, double x, double y, double z) {
        HomoCoord4f clip = Homo3D.obj2clip(localVertex(x, y, z), state);
        return clip.w <= 0 ? null : clip.toview(area).round2();
    }

    /** Build a vertex in beacon-local space; the owning Gob location is supplied by the render slot. */
    public static Coord3f localVertex(double x, double y, double z) {
        return new Coord3f((float)x, (float)-y, (float)z);
    }
}
