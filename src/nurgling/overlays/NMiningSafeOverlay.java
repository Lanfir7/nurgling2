package nurgling.overlays;

import haven.*;
import haven.render.*;
import nurgling.NGameUI;
import nurgling.actions.bots.MinesweeperSolver;
import nurgling.conf.NMiningOverlayMemory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Green dot backed by adjacent mined tiles observed without a cave-in warning.
 */
public class NMiningSafeOverlay extends Sprite implements RenderTree.Node {

    static final VertexArray.Layout pfmt = new VertexArray.Layout(
            new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 20),
            new VertexArray.Layout.Input(Tex2D.texc, new VectorFormat(2, NumberFormat.FLOAT32), 0, 12, 20)
    );

    private static TexI dotTex = null;

    final Model emod;
    ColorTex ct;
    private volatile Map<Coord, NMiningOverlayMemory.TileRef> sources = Collections.emptyMap();

    public void setSources(Map<Coord, NMiningOverlayMemory.TileRef> sources) {
        this.sources = Collections.unmodifiableMap(new HashMap<>(sources));
    }

    static boolean sourceConfirmed(NMiningOverlayMemory.TileRef saved,
                                    NMiningOverlayMemory.TileRef current,
                                    Boolean mineable, boolean warning) {
        return saved != null && saved.equals(current)
                && Boolean.FALSE.equals(mineable) && !warning;
    }

    static boolean dustWarning(String resourceName, Integer number) {
        // A rounded 0 is still a dust observation, not evidence that no dust appeared.
        return "gfx/fx/cavewarn".equals(resourceName) || number != null;
    }

    static boolean warningSprite(Sprite sprite) {
        return sprite != null && dustWarning(sprite.res == null ? null : sprite.res.name,
                sprite instanceof NMiningNumber ? ((NMiningNumber) sprite).val : null);
    }

    /** Revalidate the evidence immediately before automation mines, even between UI ticks. */
    public boolean isConfirmedSafe(NGameUI gui) {
        if (gui == null || gui.ui == null || gui.ui.sess == null) return false;
        Map<Coord, NMiningOverlayMemory.TileRef> snapshot = sources;
        if (snapshot.isEmpty()) return false;
        Set<Coord> warnings = new HashSet<>();
        synchronized (gui.ui.sess.glob.oc) {
            for (Gob gob : gui.ui.sess.glob.oc) {
                Coord tile = gob.rc.div(MCache.tilesz).floor();
                if (!snapshot.containsKey(tile)) continue;
                for (Gob.Overlay ol : gob.ols) {
                    if ((!gob.virtual && ol.spr == null)
                            || warningSprite(ol.spr)) {
                        warnings.add(tile);
                    }
                }
            }
        }
        MinesweeperSolver reader = new MinesweeperSolver(gui);
        MCache map = gui.ui.sess.glob.map;
        for (Map.Entry<Coord, NMiningOverlayMemory.TileRef> entry : snapshot.entrySet()) {
            Coord tile = entry.getKey();
            if (sourceConfirmed(entry.getValue(), NMiningOverlayMemory.ofWorld(map, tile),
                    reader.mineableOrUnknown(tile.x, tile.y), warnings.contains(tile))) return true;
        }
        return false;
    }

    private static TexI createDotTexture() {
        int size = UI.scale(64);
        BufferedImage img = TexI.mkbuf(new Coord(size, size));
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 220, 40, 255));
        int pad = UI.scale(8);
        g.fillOval(pad, pad, size - pad * 2, size - pad * 2);
        g.dispose();
        return new TexI(img);
    }

    public NMiningSafeOverlay(Owner owner) {
        super(owner, null);
        if (dotTex == null) {
            dotTex = createDotTexture();
        }
        ct = dotTex.st();
        float h = 0.22f * (float) MCache.tilesz.x;
        float[] data = {
                h, h, 1f, 1, 1,
                -h, h, 1f, 1, 0,
                -h, -h, 1f, 0, 0,
                h, -h, 1f, 0, 1,
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

    @Override
    public boolean tick(double dt) {
        return false;
    }
}
