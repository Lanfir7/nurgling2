/* Preprocessed source code */
/* $use: lib/obst */
/* $use: gfx/terobjs/arch/bounds */

package haven.res.gfx.terobjs.survobj;

import haven.*;
import haven.render.*;
import haven.res.lib.obst.*;
import haven.res.gfx.terobjs.arch.bounds.*;

/* >spr: Survey */
@haven.FromResource(name = "gfx/terobjs/survobj", version = 10, override = true)
public class Survey extends Sprite {
    public final static Indir<Resource> poleres = Resource.classres(Survey.class).pool.load("gfx/terobjs/arch/conspole", 2);
    public final static Indir<Resource> roperes = Resource.classres(Survey.class).pool.load("gfx/terobjs/consobj", 36);
    public final Sprite pole;
    public final RenderTree.Node bounds;

    public Survey(Owner owner, Resource res, Message sdt) {
	super(owner, res);
	pole = new ModSprite(owner, res);
	Gob gob = owner.context(Gob.class);
	if(sdt.eom()) {
	    bounds = null;
	} else {
	    Bounds bld = new Bounds(Sprite.create(owner, poleres.get(), Message.nil),
				    roperes.get().flayer(Material.Res.class).get(),
				    owner.context(Glob.class).map,
				    gob.getrc(), gob.a, 1f / 11f);
	    Coord2d tc = gob.rc.floor(MCache.tilesz).mul(MCache.tilesz).sub(gob.rc);
	    Coord2d ul = Coord.of(sdt.int8(), sdt.int8()).mul(MCache.tilesz).add(tc);
	    Coord2d br = Coord.of(sdt.int8(), sdt.int8()).mul(MCache.tilesz).add(tc);
	    bounds = bld.new Updated(new Obstacle(new Coord2d[][] {{
		Coord2d.of(ul.x, ul.y), Coord2d.of(br.x, ul.y),
		Coord2d.of(br.x, br.y), Coord2d.of(ul.x, br.y),
	    }}));
	}
    }

    public void added(RenderTree.Slot slot) {
	slot.add(pole);
	if(bounds != null)
	    slot.add(bounds);
    }

    public boolean tick(double dt) {
	return(pole.tick(dt));
    }

    public void gtick(Render g) {
	pole.gtick(g);
    }
}
