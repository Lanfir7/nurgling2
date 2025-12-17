/* Preprocessed source code */
import haven.*;
import haven.render.*;

/* >spr: Shield */
@haven.FromResource(name = "gfx/terobjs/items/roundshield", version = 18)
public class Shield implements Sprite.Factory {
    static final Material base = Resource.classres(Shield.class).layer(Material.Res.class, 16).get();
    static final RenderTree.Node proj = Resource.classres(Shield.class).layer(FastMesh.MeshRes.class, 0).m;

    public Sprite create(Sprite.Owner owner, Resource res, Message sdt) {
	RenderTree.Node[] parts = StaticSprite.lsparts(res, Message.nil);
	if(!sdt.eom()) {
	    UID id = sdt.uniqid();
	    Indir<Resource> dynres; try {dynres = owner.context(Resource.Resolver.class).dynres(id);} catch(NoSuchMethodError e) {dynres = Resource.classres(Shield.class).pool.dynres(id);}
	    TexRender tex = dynres.get().layer(TexR.class).tex();
	    Material sym = new Material(base, tex.draw, tex.clip);
	    parts = Utils.extend(parts, sym.apply(proj));
	}
	return(new StaticSprite(owner, res, parts));
    }
}
