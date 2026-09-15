/* Preprocessed source code */
/* $use: lib/obst */

package haven.res.gfx.terobjs.arch.bounds;

import java.util.*;
import java.util.function.*;
import haven.*;
import haven.render.*;
import haven.res.lib.obst.*;
import static haven.MCache.tilesz;

public class Bounds {
    public final RenderTree.Node pole;
    public final Material bmat;
    public final MCache map;
    public final Coord3f cc;
    public final double a;
    public final float bscale;
    public final float s, c;

    public Bounds(RenderTree.Node pole, Material bmat, MCache map, Coord3f cc, double a, float bscale) {
	this.pole = pole;
	this.bmat = bmat;
	this.map = map;
	this.cc = cc;
	this.a = a;
	this.bscale = bscale;
	this.s = (float)Math.sin(a);
	this.c = (float)Math.cos(a);
    }

    public Coord3f gnd(float rx, float ry) {
	return(new Coord3f(rx, -ry, map.getcz(rx + cc.x, ry + cc.y) - cc.z));
    }

    public Coord3f rgnd(float rx, float ry) {
	return(gnd(rx * c + ry * s, ry * c - rx * s));
    }

    public void trace(MeshBuf buf, float x1, float y1, float x2, float y2) {
	float dx = x2 - x1, dy = y2 - y1, ed = (float)Math.sqrt(dx * dx + dy * dy);
	float lx = x1, ly = y1;
	Coord3f nrm = new Coord3f(dy / ed, dx / ed, 0);
	MeshBuf.Tex tex = buf.layer(MeshBuf.tex);
	MeshBuf.Vertex ll = buf.new Vertex(gnd(lx, ly), nrm);
	MeshBuf.Vertex lh = buf.new Vertex(gnd(lx, ly).add(0, 0, 3), nrm);
	tex.set(ll, new Coord3f(0, 1, 0));
	tex.set(lh, new Coord3f(0, 0, 0));
	int lim = 0;
	while(true) {
	    boolean end = true;
	    float ma = 1.0f, a;
	    float nx = x2, ny = y2;
	    if(dx != 0) {
		float ex;
		if(dx > 0) {
		    a = ((ex = (float)((Math.floor(lx / tilesz.x) + 1) * tilesz.x)) - x1) / dx;
		} else {
		    a = ((ex = (float)((Math.ceil (lx / tilesz.x) - 1) * tilesz.x)) - x1) / dx;
		}
		if(a < ma) {
		    nx = ex; ny = y1 + dy * a;
		    ma = a;
		    end = false;
		}
	    }
	    if(dy != 0) {
		float ey;
		if(dy > 0)
		    a = ((ey = (float)((Math.floor(ly / tilesz.y) + 1) * tilesz.y)) - y1) / dy;
		else
		    a = ((ey = (float)((Math.ceil (ly / tilesz.y) - 1) * tilesz.y)) - y1) / dy;
		if(a < ma) {
		    nx = x1 + dx * a; ny = ey;
		    ma = a;
		    end = false;
		}
	    }
	    MeshBuf.Vertex nl = buf.new Vertex(gnd(nx, ny), nrm);
	    MeshBuf.Vertex nh = buf.new Vertex(gnd(nx, ny).add(0, 0, 3), nrm);
	    tex.set(nl, new Coord3f(ma * ed * bscale, 1, 0));
	    tex.set(nh, new Coord3f(ma * ed * bscale, 0, 0));
	    buf.new Face(lh, ll, nh); buf.new Face(ll, nl, nh);
	    ll = nl; lh = nh;
	    lx = nx; ly = ny;
	    if(end)
		return;
	    if(lim++ > 100)
		throw(new RuntimeException("stuck in trace"));
	}
    }

    public RenderTree.Node mkbound(Obstacle obst) {
	MeshBuf buf = new MeshBuf();
	for(Coord2d[] f : obst.p) {
	    for(int v = 0, n = f.length; v < n; v++) {
		int w = (v + 1) % n;
		float x1 = (float)f[v].x, y1 = (float)f[v].y, x2 = (float)f[w].x, y2 = (float)f[w].y;
		trace(buf, x1 * c + y1 * s, y1 * c - x1 * s, x2 * c + y2 * s, y2 * c - x2 * s);
	    }
	}
	FastMesh mesh = buf.mkmesh();
	return(bmat.apply(mesh));
    }

    public RenderTree.Node of(Obstacle obst) {
	Location[] poles;
	if(pole != null) {
	    poles = new Location[obst.verts().size()];
	    int i = 0;
	    for(Coord2d v : obst.verts())
		poles[i++] = Location.xlate(rgnd((float)v.x, (float)v.y));
	} else {
	    poles = null;
	}
	RenderTree.Node bound = mkbound(obst);
	return(new RenderTree.Node() {
		public void added(RenderTree.Slot slot) {
		    slot.ostate(Location.goback("gobx"));
		    slot.add(bound);
		    if(poles != null) {
			for(Location loc : poles)
			    slot.add(pole, loc);
		    }
		}
	    });
    }

    public class Updated implements RenderTree.Node, TickList.TickNode, TickList.Ticking {
	public final Obstacle obst;
	private final Collection<RenderTree.Slot> slots = new ArrayList<>(1);
	private RenderTree.Node current;
	private int mapseq;

	public Updated(Obstacle obst) {
	    this.mapseq = map.chseq;
	    this.obst = obst;
	    this.current = of(obst);
	}

	public TickList.Ticking ticker() {return(this);}

	private void parts(RenderTree.Slot slot) {
	    slot.add(current);
	}

	public void added(RenderTree.Slot slot) {
	    parts(slot);
	    slots.add(slot);
	}

	public void removed(RenderTree.Slot slot) {
	    slots.remove(slot);
	}

	public void autotick(double dt) {
	    int curseq = map.chseq;
	    if(curseq == this.mapseq)
		return;
	    try {
		RenderTree.Node prev = this.current;
		this.current = of(obst);
		RUtils.readd(slots, this::parts, () -> this.current = prev);
	    } catch(Loading l) {
		return;
	    }
	    this.mapseq = curseq;
	}
    }
}
