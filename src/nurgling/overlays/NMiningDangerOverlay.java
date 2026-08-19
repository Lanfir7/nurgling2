package nurgling.overlays;

import haven.*;
import haven.render.*;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Red cross on a tile that the minesweeper solver has deduced will cave in.
 * Geometry matches {@link NMiningNumber}.
 */
public class NMiningDangerOverlay extends Sprite implements RenderTree.Node {

    static final VertexArray.Layout pfmt = new VertexArray.Layout(
            new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 20),
            new VertexArray.Layout.Input(Tex2D.texc, new VectorFormat(2, NumberFormat.FLOAT32), 0, 12, 20)
    );

    private static TexI crossTex = null;

    final Model emod;
    Gob gob;
    ColorTex ct;

    private static TexI createCrossTexture() {
        int size = UI.scale(64);
        BufferedImage img = TexI.mkbuf(new Coord(size, size));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255, 0, 0, 255));
        g.setStroke(new BasicStroke(UI.scale(5), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int margin = UI.scale(8);
        g.drawLine(size / 2, margin, size / 2, size - margin);
        g.drawLine(margin, size / 2, size - margin, size / 2);
        g.dispose();
        return new TexI(img);
    }

    public NMiningDangerOverlay(Owner owner) {
        super(owner, null);
        gob = (Gob) owner;
        if (crossTex == null) {
            crossTex = createCrossTexture();
        }
        ct = crossTex.st();
        float[] data = {
                (float) (0.5f * MCache.tilesz.x), (float) (0.5f * MCache.tilesz.y), 1f, 1, 1,
                -(float) (0.5f * MCache.tilesz.x), (float) (0.5f * MCache.tilesz.y), 1f, 1, 0,
                -(float) (0.5f * MCache.tilesz.x), -(float) (0.5f * MCache.tilesz.y), 1f, 0, 0,
                (float) (0.5f * MCache.tilesz.x), -(float) (0.5f * MCache.tilesz.y), 1f, 0, 1,
        };
        VertexArray va = new VertexArray(pfmt,
                new VertexArray.Buffer((4) * pfmt.inputs[0].stride, DataBuffer.Usage.STATIC,
                        DataBuffer.Filler.of(data)));
        this.emod = new Model(Model.Mode.TRIANGLE_FAN, va, null);
    }

    public void added(RenderTree.Slot slot) {
        Pipe.Op rmat = Pipe.Op.compose(ct, Clickable.No, Rendered.postpfx, States.Depthtest.none);
        slot.add(emod, rmat);
    }

    @Override
    public boolean tick(double dt) {
        return false;
    }
}
