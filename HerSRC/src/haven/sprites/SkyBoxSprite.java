
package haven.sprites;

import haven.*;
import haven.render.*;
import haven.render.sl.*;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import static haven.render.sl.Cons.*;

public class SkyBoxSprite extends Sprite {

	public static final TextureCube.SamplerCube clouds = new TextureCube.SamplerCube(new RUtils.CubeFill(() -> Resource.local().loadwait("customclient/skybox/clouds").layer(Resource.imgc).img).mktex());
	public static final TextureCube.SamplerCube galaxy = new TextureCube.SamplerCube(new RUtils.CubeFill(() -> Resource.local().loadwait("customclient/skybox/galaxy").layer(Resource.imgc).img).mktex());
	static final Pipe.Op smat;
	VertexBuf.VertexData posa;
	VertexBuf vbuf;
	Model smod;

	public SkyBoxSprite(final Owner owner, final Resource resource) {
		super(owner, resource);
		// ND: the wrapmode fixes the texture edges being off by 1 pixel. I don't understand render code, but chatgpt figured it out
		clouds.wrapmode(Texture.Wrapping.CLAMP);
		galaxy.wrapmode(Texture.Wrapping.CLAMP);
		init();
	}


	private void init() {
		int max = 6;
		float size = 2500.0f;

		FloatBuffer wfbuf = Utils.wfbuf(max * 3 * 2);
		FloatBuffer wfbuf2 = Utils.wfbuf(max * 3 * 2);

		wfbuf.put(0, -size).put(1, -size).put(2, size);
		wfbuf2.put(0, 0).put(1, 0).put(2, 1);

		wfbuf.put(3, size).put(4, -size).put(5, size);
		wfbuf2.put(3, 0).put(4, 0).put(5, 1);

		wfbuf.put(6, size).put(7, size).put(8, size);
		wfbuf2.put(6, 0).put(7, 0).put(8, 1);

		wfbuf.put(9, -size).put(10, size).put(11, size);
		wfbuf2.put(9, 0).put(10, 0).put(11, 1);

		// Bottom face vertices (z = -size):
		wfbuf.put(12, -size).put(13, -size).put(14, -size);
		wfbuf2.put(12, 0).put(13, 0).put(14, -1);

		wfbuf.put(15, size).put(16, -size).put(17, -size);
		wfbuf2.put(15, 0).put(16, 0).put(17, -1);

		wfbuf.put(18, size).put(19, size).put(20, -size);
		wfbuf2.put(18, 0).put(19, 0).put(20, -1);

		wfbuf.put(21, -size).put(22, size).put(23, -size);
		wfbuf2.put(21, 0).put(22, 0).put(23, -1);

//		for (int i = 8; i < 12; i++) {
//			int off = i * 3;
//			wfbuf.put(off, -size).put(off + 1, -size).put(off + 2, size);
//			wfbuf2.put(off, 0).put(off + 1, 0).put(off + 2, 1);
//		}

		this.posa = new VertexBuf.VertexData(wfbuf);
		this.vbuf = new VertexBuf(this.posa, new VertexBuf.NormalData(wfbuf2));

		this.smod = new Model(Model.Mode.TRIANGLES, this.vbuf.data(),
				new Model.Indices(max * 6, NumberFormat.UINT16, DataBuffer.Usage.STATIC, this::sidx));
	}

	private FillBuffer sidx(final Model.Indices indices, final Environment environment) {
		final FillBuffer fillbuf = environment.fillbuf(indices);
		final ShortBuffer sb = fillbuf.push().asShortBuffer();

		for (int i = 0; i < 6; i++) {
			int base = i * 6;
			switch (i) {
				case 0: // top
					sb.put(base, (short) 0).put(base + 1, (short) 1).put(base + 2, (short) 2);
					sb.put(base + 3, (short) 0).put(base + 4, (short) 2).put(base + 5, (short) 3);
					break;
				case 1: // bottom (flipped)
					sb.put(base, (short) 4).put(base + 1, (short) 6).put(base + 2, (short) 5);
					sb.put(base + 3, (short) 4).put(base + 4, (short) 7).put(base + 5, (short) 6);
					break;
				case 2: // front (flipped)
					sb.put(base, (short) 0).put(base + 1, (short) 5).put(base + 2, (short) 1);
					sb.put(base + 3, (short) 0).put(base + 4, (short) 4).put(base + 5, (short) 5);
					break;
				case 3: // back
					sb.put(base, (short) 3).put(base + 1, (short) 2).put(base + 2, (short) 6);
					sb.put(base + 3, (short) 3).put(base + 4, (short) 6).put(base + 5, (short) 7);
					break;
				case 4: // left
					sb.put(base, (short) 0).put(base + 1, (short) 3).put(base + 2, (short) 7);
					sb.put(base + 3, (short) 0).put(base + 4, (short) 7).put(base + 5, (short) 4);
					break;
				case 5: // right (flipped)
					sb.put(base, (short) 1).put(base + 1, (short) 6).put(base + 2, (short) 2);
					sb.put(base + 3, (short) 1).put(base + 4, (short) 5).put(base + 5, (short) 6);
					break;
			}
		}

		return fillbuf;
	}

	public void added(final RenderTree.Slot slot) {
		slot.ostate(Pipe.Op.compose(new States.Facecull(States.Facecull.Mode.FRONT), Location.goback("gobx"), new SkyboxShader(), Clickable.notClickable));
		slot.add(this.smod, smat);
	}

	static {
		smat = new BaseColor(new Color(255, 255, 255, 255));
	}

	private static final State.Slot<State> surfslot = new State.Slot<>(State.Slot.Type.DRAW, State.class);
	public static class SkyboxShader extends State {
		public static Uniform ssky = new Uniform(Type.SAMPLERCUBE, p -> {
			int style = Utils.getprefi("skyboxStyle", 0);
			switch (style) {
				case 0:
					return clouds;
				case 1:
					return galaxy;
				default:
					return clouds; // Fallback
			}
		});
		private final Uniform icam = new Uniform(Type.MAT3, p -> Homo3D.camxf(p).transpose(), Homo3D.cam);

		private SkyboxShader() {
		}

		private ShaderMacro shader = new ShaderMacro() {
			public void modify(final ProgramContext prog) {
				Homo3D.fragedir(prog.fctx);
				FragColor.fragcol(prog.fctx).mod(in -> mul(in, textureCube(ssky.ref(), mul(icam.ref(), reflect(Homo3D.fragedir(prog.fctx).depref(), Homo3D.frageyen(prog.fctx).depref())))),0);
			}
		};

		public ShaderMacro shader() {return(shader);}

		public void apply(Pipe buf) {
			buf.put(surfslot, this);
		}
	}

}
