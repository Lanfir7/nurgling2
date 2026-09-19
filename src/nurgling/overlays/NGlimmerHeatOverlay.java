package nurgling.overlays;

import haven.*;
import haven.render.*;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

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
            return new Color(212, 168, 72, 150);
        }
        if (heat == 2) {
            return new Color(232, 190, 86, 175);
        }
        return new Color(255, 214, 110, 200);
    }

    private static void keepFillRgb(BufferedImage img, Color fill) {
        byte r = (byte) fill.getRed();
        byte gc = (byte) fill.getGreen();
        byte b = (byte) fill.getBlue();
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < data.length; i += 4) {
            if (data[i + 3] != 0) {
                data[i] = r;
                data[i + 1] = gc;
                data[i + 2] = b;
            }
        }
    }

    private static TexI makeTex(int heat) {
        int size = UI.scale(64);
        BufferedImage img = TexI.mkbuf(new Coord(size, size));
        Color fill = fillFor(heat);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.Src);
        g.setColor(fill);
        int pad = UI.scale(5);
        g.fillRoundRect(pad, pad, size - pad * 2, size - pad * 2, UI.scale(16), UI.scale(16));
        g.dispose();
        keepFillRgb(img, fill);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(255, 248, 220, 235));
        g.setFont(new Font("SansSerif", Font.BOLD, UI.scale(26)));
        String s = heat > 9 ? "9" : String.valueOf(heat);
        int w = g.getFontMetrics().stringWidth(s);
        int h = g.getFontMetrics().getAscent();
        g.drawString(s, (size - w) / 2, (size + h) / 2 - UI.scale(4));
        g.dispose();
        return new TexI(img, false);
    }

    public NGlimmerHeatOverlay(Owner owner, int val) {
        super(owner, null);
        this.val = Math.max(1, val);
        ct = makeTex(this.val).st();
        float hx = 0.5f * (float) MCache.tilesz.x;
        float hy = 0.5f * (float) MCache.tilesz.y;
        float z = 0.35f;
        float[] data = {
                hx, hy, z, 1, 1,
                -hx, hy, z, 1, 0,
                -hx, -hy, z, 0, 0,
                hx, -hy, z, 0, 1,
        };
        VertexArray va = new VertexArray(pfmt,
                new VertexArray.Buffer(4 * pfmt.inputs[0].stride, DataBuffer.Usage.STATIC,
                        DataBuffer.Filler.of(data)));
        this.emod = new Model(Model.Mode.TRIANGLE_FAN, va, null);
    }

    public void added(RenderTree.Slot slot) {
        Pipe.Op rmat = Pipe.Op.compose(
                ct,
                Clickable.No,
                new States.Depthtest(States.Depthtest.Test.LE),
                FragColor.blend(new BlendMode(
                        BlendMode.Function.ADD, BlendMode.Factor.SRC_ALPHA, BlendMode.Factor.INV_SRC_ALPHA,
                        BlendMode.Function.ADD, BlendMode.Factor.ONE, BlendMode.Factor.INV_SRC_ALPHA)));
        slot.add(emod, rmat);
    }

    public boolean tick(double dt) {
        return false;
    }
}
