package nurgling.overlays;

import haven.*;
import haven.render.*;
import haven.render.Model.Indices;
import nurgling.conf.NAreaRadStyle;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collection;

public class NAreaRad extends Sprite {

	public enum Palette { ANIMAL, BEEHIVE, CUSTOM }

	Pipe.Op smat;
	Pipe.Op emat;
	final VertexBuf.VertexData posa;
	final VertexBuf vbuf;
	final Model smod, emod;
	private Coord2d lc;
	int n;
	float curR;
	Palette palette = Palette.ANIMAL;
	Color customFill;
	Color customEdge;
	private int lastSig = Integer.MIN_VALUE;
	private final Collection<RenderTree.Slot> slots = new ArrayList<>(1);

	public NAreaRad(Owner owner, float r) {
		this(owner, r, Palette.ANIMAL);
	}

	public NAreaRad(Owner owner, float r, Palette palette) {
		super(owner, null);
		this.palette = palette;
		this.curR = r;
		float h = NAreaRadStyle.bandHeight();
		n = Math.max(48, (int)(2 * Math.PI * Math.max(r, 1) / 11.0));
		FloatBuffer posb = Utils.wfbuf(n * 3 * 2);
		FloatBuffer nrmb = Utils.wfbuf(n * 3 * 2);
		for(int i = 0; i < n; i++) {
			float s = (float)Math.sin(2 * Math.PI * i / n);
			float c = (float)Math.cos(2 * Math.PI * i / n);
			posb.put(     i  * 3 + 0, c * r).put(     i  * 3 + 1, s * r).put(     i  * 3 + 2,  h);
			posb.put((n + i) * 3 + 0, c * r).put((n + i) * 3 + 1, s * r).put((n + i) * 3 + 2, -h);
			nrmb.put(     i  * 3 + 0, c).put(     i  * 3 + 1, s).put(     i  * 3 + 2, 0);
			nrmb.put((n + i) * 3 + 0, c).put((n + i) * 3 + 1, s).put((n + i) * 3 + 2, 0);
		}
		VertexBuf.VertexData posa = new VertexBuf.VertexData(posb);
		VertexBuf.NormalData nrma = new VertexBuf.NormalData(nrmb);
		VertexBuf vbuf = new VertexBuf(posa, nrma);
		this.smod = new Model(Model.Mode.TRIANGLES, vbuf.data(), new Indices(n * 6, NumberFormat.UINT16, DataBuffer.Usage.STATIC, this::sidx));
		this.emod = new Model(Model.Mode.LINE_STRIP, vbuf.data(), new Indices(n + 1, NumberFormat.UINT16, DataBuffer.Usage.STATIC, this::eidx));
		this.posa = posa;
		this.vbuf = vbuf;
		applyStyle();
	}

	public NAreaRad(Owner owner,  float r, Color smatc, Color ematc) {
		this(owner, r, Palette.CUSTOM);
		this.customFill = smatc;
		this.customEdge = ematc;
		applyStyle();
	}

	private FillBuffer sidx(Indices dst, Environment env) {
		FillBuffer ret = env.fillbuf(dst);
		ShortBuffer buf = ret.push().asShortBuffer();
		for(int i = 0, n = dst.n / 6; i < n; i++) {
			int b = i * 6;
			buf.put(b, (short)i).put(b + 1, (short)(i + n)).put(b + 2, (short)((i + 1) % n));
			buf.put(b + 3, (short)(i + n)).put(b + 4, (short)(((i + 1) % n) + n)).put(b + 5, (short)((i + 1) % n));
		}
		return(ret);
	}

	private FillBuffer eidx(Indices dst, Environment env) {
		FillBuffer ret = env.fillbuf(dst);
		ShortBuffer buf = ret.push().asShortBuffer();
		for(int i = 0; i < dst.n - 1; i++)
			buf.put(i, (short)i);
		buf.put(dst.n - 1, (short)0);
		return(ret);
	}

	private Color[] paletteColors() {
		switch (palette) {
			case BEEHIVE:
				return new Color[] { NAreaRadStyle.beehiveFill(), NAreaRadStyle.beehiveEdge() };
			case CUSTOM:
				return new Color[] {
					customFill != null ? customFill : NAreaRadStyle.DEF_ANIMAL_FILL,
					customEdge != null ? customEdge : NAreaRadStyle.DEF_ANIMAL_EDGE
				};
			default:
				return new Color[] { NAreaRadStyle.animalFill(), NAreaRadStyle.animalEdge() };
		}
	}

	private boolean noClick() {
		return palette != Palette.ANIMAL;
	}

	private int styleSig() {
		Color[] cols = paletteColors();
		return java.util.Objects.hash(palette, NAreaRadStyle.bandHeight(), NAreaRadStyle.lineWidth(),
				cols[0].getRGB(), cols[1].getRGB());
	}

	private void applyStyle() {
		Color[] cols = paletteColors();
		float lw = NAreaRadStyle.lineWidth();
		if (noClick()) {
			smat = Pipe.Op.compose(new BaseColor(cols[0]), Clickable.No);
			emat = Pipe.Op.compose(new BaseColor(cols[1]), new States.LineWidth(lw), Clickable.No);
		} else {
			smat = new BaseColor(cols[0]);
			emat = Pipe.Op.compose(new BaseColor(cols[1]), new States.LineWidth(lw));
		}
	}

	private void rebuildSlots() {
		for (RenderTree.Slot slot : slots) {
			slot.clear();
			slot.add(smod, smat);
			slot.add(emod, emat);
		}
	}

	private void setz(Render g, Glob glob, Coord2d c) {
		int n = this.n;
		FloatBuffer posb = posa.data;
		float h = NAreaRadStyle.bandHeight();
		try {
			float bz = (float)glob.map.getcz(c.x, c.y);
			for(int i = 0; i < n; i++) {
				float z = (float)glob.map.getcz(c.x + posb.get(i * 3), c.y - posb.get(i * 3 + 1)) - bz;
				posb.put(i * 3 + 2, z + h);
				posb.put((n + i) * 3 + 2, z - h);
			}
		} catch(Loading e) {
			return;
		}
		vbuf.update(g);
	}

	void setR(Render g, float r){
		this.curR = r;
		float h = NAreaRadStyle.bandHeight();
		FloatBuffer posb = posa.data;
		for(int i = 0; i < n; i++) {
			float s = (float)Math.sin(2 * Math.PI * i / n);
			float c = (float)Math.cos(2 * Math.PI * i / n);
			posb.put(     i  * 3 + 0, c * r).put(     i  * 3 + 1, s * r).put(     i  * 3 + 2,  h);
			posb.put((n + i) * 3 + 0, c * r).put((n + i) * 3 + 1, s * r).put((n + i) * 3 + 2, -h);
		}
		this.vbuf.update(g);
	}

	public void gtick(Render g) {
		int sig = styleSig();
		if (sig != lastSig) {
			applyStyle();
			lastSig = sig;
			if (curR != 0)
				setR(g, curR);
			rebuildSlots();
		}
		Coord2d cc = ((Gob)owner).rc;
		if((lc == null) || !lc.equals(cc)) {
			setz(g, owner.context(Glob.class), cc);
			lc = cc;
		}
	}

	public void added(RenderTree.Slot slot) {
		slots.add(slot);
		slot.ostate(Pipe.Op.compose(Rendered.postpfx,
				new States.Facecull(States.Facecull.Mode.NONE),
				Location.goback("gobx")));
		slot.add(smod, smat);
		slot.add(emod, emat);
	}

	@Override
	public void removed(RenderTree.Slot slot) {
		slots.remove(slot);
	}

	@Override
	public boolean tick(double dt) {
		String pose = ((Gob)owner).pose();
		if(pose!=null && NParser.checkName(pose, new NAlias("dead", "knock")))
			return true;
		return super.tick(dt);
	}
}
