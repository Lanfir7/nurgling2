package haven.res.gfx.fx.bprad;

import haven.Coord2d;
import haven.FromResource;
import haven.Glob;
import haven.Gob;
import haven.Loading;
import haven.Message;
import haven.Resource;
import haven.Sprite;
import haven.Utils;
import haven.VertexBuf;
import haven.render.BaseColor;
import haven.render.DataBuffer;
import haven.render.Environment;
import haven.render.FillBuffer;
import haven.render.Location;
import haven.render.Model;
import haven.render.NumberFormat;
import haven.render.Pipe;
import haven.render.Render;
import haven.render.RenderTree;
import haven.render.Rendered;
import haven.render.States;
import nurgling.tools.FlatWorld;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

@FromResource(name = "gfx/fx/bprad", version = 9)
public class BPRad extends Sprite {
    private static final float BAND_HEIGHT = 10f;
    private static final Pipe.Op smat = new BaseColor(new Color(192, 0, 0, 128));
    private static final Pipe.Op emat = Pipe.Op.compose(
            new BaseColor(new Color(255, 224, 96)), new States.LineWidth(4));

    final Gob gob;
    final VertexBuf.VertexData posa;
    final VertexBuf vbuf;
    final Model smod;
    final Model emod;
    private Coord2d lc;
    private boolean lastFlat;
    float[] barda;

    public BPRad(Owner owner, Resource res, float radius) {
        super(owner, res);
        gob = owner.context(Gob.class);
        int n = Math.max(24, (int)(2 * Math.PI * radius / 11.0));
        FloatBuffer posb = Utils.wfbuf(n * 3 * 2);
        FloatBuffer nrmb = Utils.wfbuf(n * 3 * 2);
        for(int i = 0; i < n; i++) {
            float s = (float)Math.sin(2 * Math.PI * i / n);
            float c = (float)Math.cos(2 * Math.PI * i / n);
            posb.put(i * 3, c * radius).put((i * 3) + 1, s * radius).put((i * 3) + 2, BAND_HEIGHT);
            posb.put((n + i) * 3, c * radius).put(((n + i) * 3) + 1, s * radius)
                    .put(((n + i) * 3) + 2, -BAND_HEIGHT);
            nrmb.put(i * 3, c).put((i * 3) + 1, s).put((i * 3) + 2, 0);
            nrmb.put((n + i) * 3, c).put(((n + i) * 3) + 1, s).put(((n + i) * 3) + 2, 0);
        }
        posa = new VertexBuf.VertexData(posb);
        VertexBuf.NormalData nrma = new VertexBuf.NormalData(nrmb);
        vbuf = new VertexBuf(posa, nrma);
        smod = new Model(Model.Mode.TRIANGLES, vbuf.data(),
                new Model.Indices(n * 6, NumberFormat.UINT16, DataBuffer.Usage.STATIC, this::sidx));
        emod = new Model(Model.Mode.LINE_STRIP, vbuf.data(),
                new Model.Indices(n + 1, NumberFormat.UINT16, DataBuffer.Usage.STATIC, this::eidx));
    }

    public BPRad(Owner owner, Resource res, Message sdt) {
        this(owner, res, Utils.hfdec((short)sdt.int16()) * 11f);
    }

    private FillBuffer sidx(Model.Indices dst, Environment env) {
        FillBuffer ret = env.fillbuf(dst);
        ShortBuffer buf = ret.push().asShortBuffer();
        for(int i = 0, n = dst.n / 6; i < n; i++) {
            int b = i * 6;
            buf.put(b, (short)i).put(b + 1, (short)(i + n)).put(b + 2, (short)((i + 1) % n));
            buf.put(b + 3, (short)(i + n)).put(b + 4, (short)(((i + 1) % n) + n))
                    .put(b + 5, (short)((i + 1) % n));
        }
        return ret;
    }

    private FillBuffer eidx(Model.Indices dst, Environment env) {
        FillBuffer ret = env.fillbuf(dst);
        ShortBuffer buf = ret.push().asShortBuffer();
        for(int i = 0; i < dst.n - 1; i++)
            buf.put(i, (short)i);
        buf.put(dst.n - 1, (short)0);
        return ret;
    }

    private boolean setz(Render g, Glob glob, Coord2d c, boolean flat) {
        FloatBuffer posb = posa.data;
        int n = posa.size() / 2;
        try {
            float bz = (float)glob.map.getcz(c.x, c.y);
            for(int i = 0; i < n; i++) {
                float z = FlatWorld.overlayRelZ(flat,
                        glob.map.getcz(c.x + posb.get(i * 3), c.y - posb.get((i * 3) + 1)), bz);
                posb.put((i * 3) + 2, z + BAND_HEIGHT);
                posb.put(((n + i) * 3) + 2, z - BAND_HEIGHT);
            }
        } catch(Loading e) {
            return false;
        }
        vbuf.update(g);
        return true;
    }

    @Override
    public void gtick(Render g) {
        Coord2d cc = gob.rc;
        boolean flat = FlatWorld.isEnabled();
        if((lc == null) || !lc.equals(cc) || (flat != lastFlat)) {
            if(setz(g, owner.context(Glob.class), cc, flat)) {
                lc = cc;
                lastFlat = flat;
            }
        }
    }

    @Override
    public void added(RenderTree.Slot slot) {
        slot.ostate(Pipe.Op.compose(Rendered.postpfx,
                new States.Facecull(States.Facecull.Mode.NONE), Location.goback("gobx")));
        slot.add(smod, smat);
        slot.add(emod, emat);
    }
}
