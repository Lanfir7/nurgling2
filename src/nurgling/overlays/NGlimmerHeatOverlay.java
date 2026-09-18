package nurgling.overlays;

import haven.*;
import haven.render.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class NGlimmerHeatOverlay extends Sprite implements RenderTree.Node {
    static final VertexArray.Layout pfmt = new VertexArray.Layout(
            new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 20),
            new VertexArray.Layout.Input(Tex2D.texc, new VectorFormat(2, NumberFormat.FLOAT32), 0, 12, 20)
    );

    public final int val;
    final Model emod;
    ColorTex ct;

    private static Color fillFor(int heat) {
        if (heat <= 1) {
            return new Color(107, 78, 24, 210);
        }
        if (heat == 2) {
            return new Color(160, 120, 32, 230);
        }
        return new Color(240, 195, 90, 245);
    }

    private static TexI makeTex(int heat) {
        int size = UI.scale(64);
        BufferedImage img = TexI.mkbuf(new Coord(size, size));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fillFor(heat));
        int pad = UI.scale(4);
        g.fillRoundRect(pad, pad, size - pad * 2, size - pad * 2, UI.scale(10), UI.scale(10));
        g.setColor(new Color(26, 18, 8, 255));
        g.setFont(new Font("SansSerif", Font.BOLD, UI.scale(28)));
        String s = heat > 9 ? "9" : String.valueOf(heat);
        int w = g.getFontMetrics().stringWidth(s);
        int h = g.getFontMetrics().getAscent();
        g.drawString(s, (size - w) / 2, (size + h) / 2 - UI.scale(4));
        g.dispose();
        return new TexI(img);
    }

    public NGlimmerHeatOverlay(Owner owner, int val) {
        super(owner, null);
        this.val = Math.max(1, val);
        ct = makeTex(this.val).st();
        float hx = 0.5f * (float) MCache.tilesz.x;
        float hy = 0.5f * (float) MCache.tilesz.y;
        float[] data = {
                hx, hy, 1f, 1, 1,
                -hx, hy, 1f, 1, 0,
                -hx, -hy, 1f, 0, 0,
                hx, -hy, 1f, 0, 1,
        };
        VertexArray va = new VertexArray(pfmt,
                new VertexArray.Buffer(4 * pfmt.inputs[0].stride, DataBuffer.Usage.STATIC,
                        DataBuffer.Filler.of(data)));
        this.emod = new Model(Model.Mode.TRIANGLE_FAN, va, null);
    }

    public void added(RenderTree.Slot slot) {
        Pipe.Op rmat = Pipe.Op.compose(ct, Clickable.No, Rendered.postpfx, States.Depthtest.none);
        slot.add(emod, rmat);
    }

    public boolean tick(double dt) {
        return false;
    }
}
