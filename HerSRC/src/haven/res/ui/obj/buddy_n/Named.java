/* Preprocessed source code */
/* $use: ui/obj/buddy */

package haven.res.ui.obj.buddy_n;

import haven.*;
import haven.res.ui.obj.buddy.*;
import java.awt.Color;
import java.awt.image.BufferedImage;

/* >objdelta: Named */
@haven.FromResource(name = "ui/obj/buddy-n", version = 4)
public class Named extends GAttrib implements InfoPart {
    public final Info info;
    public final String nm;
    public final Color col;
    public final boolean auto;

    public Named(Gob gob, String nm, Color col, boolean auto) {
	super(gob);
	this.nm = nm;
	this.col = col;
	this.auto = auto;
	info = Info.add(gob, this);
    }

    public static void parse(Gob gob, Message dat) {
	String nm = dat.string();
	if(nm.length() > 0) {
	    Color col = BuddyWnd.gc[dat.uint8()];
	    int fl = dat.uint8();
	    gob.setattr(new Named(gob, nm, col, (fl & 1) != 0));
	} else {
	    gob.delattr(Named.class);
	}
    }

    public void dispose() {
	super.dispose();
	info.remove(this);
    }

    public void draw(CompImage cmp, RenderContext ctx) {
	cmp.add(InfoPart.rendertext(nm, col), Coord.z);
    }

    public boolean auto() {return(auto);}
}
