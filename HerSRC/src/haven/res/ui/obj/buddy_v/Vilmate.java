/* Preprocessed source code */
/* $use: ui/obj/buddy */

package haven.res.ui.obj.buddy_v;

import haven.*;
import haven.res.ui.obj.buddy.*;
import java.awt.image.BufferedImage;

/* >objdelta: Vilmate */
@haven.FromResource(name = "ui/obj/buddy-v", version = 4)
public class Vilmate extends GAttrib implements InfoPart {
    public static final BufferedImage icon = Resource.classres(Vilmate.class).layer(Resource.imgc, 0).scaled();
    public final Info info;

    public Vilmate(Gob gob) {
	super(gob);
	info = Info.add(gob, this);
    }

    public static void parse(Gob gob, Message dat) {
	int fl = dat.uint8();
	if((fl & 1) != 0)
	    gob.setattr(new Vilmate(gob));
	else
	    gob.delattr(Vilmate.class);
    }

    public void dispose() {
	super.dispose();
	info.remove(this);
    }

    public void draw(CompImage cmp, RenderContext ctx) {
	int x = cmp.sz.x;
	if(x > 0)
	    x += UI.scale(1);
	cmp.add(icon, Coord.of(x, 0));
    }
}
