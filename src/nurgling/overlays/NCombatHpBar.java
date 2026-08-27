package nurgling.overlays;

import haven.*;
import haven.render.*;
import nurgling.NUtils;
import nurgling.tools.CreatureHp;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * HP bar above a gob that is currently in combat ({@code Fightview.lsrel}).
 * Anchored in the world, drawn in screen pixels so it stays readable from the camera.
 */
public class NCombatHpBar extends Sprite implements RenderTree.Node, PView.Render2D {
    private static final Coord3f POS = new Coord3f(0, 0, 13.2f);
    private static final Coord BAR = UI.scale(new Coord(78, 22));
    private static final int PAD = UI.scale(3);
    private static final float ARC = UI.scale(8f);
    private static final Text.Foundry NUM = new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 9, Color.WHITE).aa(true);

    private final Gob gob;
    private String lastKey;
    private TexI lastTex;

    public NCombatHpBar(Owner owner) {
        super(owner, null);
        this.gob = (Gob) owner;
    }

    private int dealt() {
        Gob.Overlay gol = gob.findol(NDMGOverlay.class);
        if((gol != null) && (gol.spr instanceof NDMGOverlay))
            return ((NDMGOverlay)gol.spr).total();
        return 0;
    }

    private String resName() {
        return (gob.ngob != null) ? gob.ngob.name : null;
    }

    @Override
    public boolean tick(double dt) {
        if((NUtils.getGameUI() == null) || (NUtils.getGameUI().fv == null))
            return true;
        for(Fightview.Relation rel : NUtils.getGameUI().fv.lsrel) {
            if((rel != null) && (rel.gobid == gob.id))
                return false;
        }
        return true;
    }

    @Override
    public void draw(GOut g, Pipe state) {
        Integer max = CreatureHp.maxHp(resName());
        if(max == null)
            return;
        Coord sc = Homo3D.obj2view(POS, state, Area.sized(g.sz())).round2();
        if(sc == null)
            return;
        int dealt = dealt();
        String text = CreatureHp.remainingLabel(dealt, resName());
        if(text == null)
            return;
        float frac = CreatureHp.fraction(dealt, max);
        int fw = Math.max(0, Math.round(BAR.x * frac));
        String key = text + "/" + fw;
        if(!key.equals(lastKey)) {
            lastKey = key;
            if(lastTex != null)
                lastTex.dispose();
            lastTex = new TexI(paint(frac, fw, text));
        }
        g.image(lastTex, sc.sub(lastTex.sz().div(2)));
    }

    private static BufferedImage paint(float frac, int fw, String text) {
        int w = BAR.x + PAD * 2;
        int h = BAR.y + PAD * 2;
        BufferedImage img = TexI.mkbuf(new Coord(w, h));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float x = PAD, y = PAD, bw = BAR.x, bh = BAR.y;
        float arc = ARC;
        float iarc = Math.max(2f, arc - 2f);

        /* Drop shadow for a bit of lift. */
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Float(x + 1, y + 2, bw, bh, arc, arc));

        RoundRectangle2D body = new RoundRectangle2D.Float(x, y, bw, bh, arc, arc);

        /* Track: inset well. */
        g.setPaint(new GradientPaint(x, y, new Color(28, 30, 34, 235), x, y + bh, new Color(6, 7, 9, 235)));
        g.fill(body);

        /* Metallic rim. */
        g.setColor(new Color(52, 56, 64, 220));
        g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, bw - 1, bh - 1, arc, arc));
        g.setColor(new Color(0, 0, 0, 180));
        g.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, bw - 3, bh - 3, iarc, iarc));

        if(fw > 0) {
            Color mid = CreatureHp.fillColor(frac);
            Color hi = tint(mid, 0.22f);
            Color lo = shade(mid, 0.48f);
            java.awt.Shape old = g.getClip();
            g.setClip(new java.awt.Rectangle(Math.round(x), Math.round(y), fw, Math.round(bh)));
            RoundRectangle2D fill = new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, bw - 3, bh - 3, iarc, iarc);
            g.setPaint(new GradientPaint(x, y, hi, x, y + bh, lo));
            g.fill(fill);
            /* Gloss along the top. */
            g.setPaint(new GradientPaint(x, y + 1.5f, new Color(255, 255, 255, 50),
                    x, y + bh * 0.4f, new Color(255, 255, 255, 0)));
            g.fill(fill);
            g.setClip(old);
        }

        BufferedImage num = Utils.outline2(NUM.render(text).img, Color.BLACK);
        int nx = Math.round(x + (bw - num.getWidth()) / 2f);
        int ny = Math.round(y + (bh - num.getHeight()) / 2f);
        g.setColor(new Color(0, 0, 0, 110));
        float scrimPad = UI.scale(2f);
        g.fill(new RoundRectangle2D.Float(nx - scrimPad, ny - 1, num.getWidth() + scrimPad * 2,
                num.getHeight() + 2, ARC * 0.5f, ARC * 0.5f));
        g.drawImage(num, nx, ny, null);
        g.dispose();
        return img;
    }

    private static Color shade(Color c, float m) {
        return new Color(clamp((int)(c.getRed() * m)), clamp((int)(c.getGreen() * m)),
                clamp((int)(c.getBlue() * m)), 255);
    }

    private static Color tint(Color c, float m) {
        return new Color(
                clamp((int)(c.getRed() + (255 - c.getRed()) * m)),
                clamp((int)(c.getGreen() + (255 - c.getGreen()) * m)),
                clamp((int)(c.getBlue() + (255 - c.getBlue()) * m)),
                255);
    }

    private static int clamp(int v) {
        return (v < 0) ? 0 : (v > 255 ? 255 : v);
    }
}
