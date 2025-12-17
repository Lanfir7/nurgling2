/* Preprocessed source code */
package haven.res.lib.gplant;

import haven.*;
import haven.render.*;
import haven.resutil.*;
import java.util.*;
import java.util.function.*;
import haven.Sprite.Owner;

/* >spr: GaussianPlant */
@haven.FromResource(name = "lib/gplant", version = 1)
public class GaussianPlant implements Sprite.Factory {
    public final int numl, numh;
    public final float r;
    public final List<Collection<Function<Owner, RenderTree.Node>>> var;

    public GaussianPlant(Resource res, int numl, int numh, float r) {
	this.numl = numl;
	this.numh = numh;
	this.r = r;
	Map<Integer, Collection<Function<Owner, RenderTree.Node>>> vars = new HashMap<>();
	for(FastMesh.MeshRes mr : res.layers(FastMesh.MeshRes.class)) {
	    RenderTree.Node w = mr.mat.get().apply(mr.m);
	    vars.computeIfAbsent(mr.id, k -> new ArrayList<>()).add(o -> w);
	}
	for(RenderLink.Res lr : res.layers(RenderLink.Res.class)) {
	    vars.computeIfAbsent(lr.id, k -> new ArrayList<>()).add(lr.l::make);
	}
	this.var = new ArrayList<>(vars.values());
    }

    public GaussianPlant(Resource res, Object[] args) {
	this(res, ((Number)args[0]).intValue(), ((Number)args[1]).intValue(), ((Number)args[2]).floatValue());
    }

    public Sprite create(Sprite.Owner owner, Resource res, Message sdt) {
	Random rnd = owner.mkrandoom();
	CSprite spr = new CSprite(owner, res);
	int num = rnd.nextInt(numh - numl + 1) + numl;
	if (OptWnd.simplifiedForageablesCheckBox.a){
		spr.addpart(0, 0, Pipe.Op.nil, var.get(0).iterator().next().apply(owner));
	}
	else
	for(int i = 0; i < num; i++) {
	    float x = (float)rnd.nextGaussian() * r, y = (float)rnd.nextGaussian() * r;
	    for(Function<Owner, RenderTree.Node> mk : var.get(rnd.nextInt(var.size())))
		spr.addpart(x, y, Pipe.Op.nil, mk.apply(owner));
	}
	return(spr);
    }
}
