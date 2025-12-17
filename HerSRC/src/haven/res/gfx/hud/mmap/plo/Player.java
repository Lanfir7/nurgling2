/* Preprocessed source code */
/* $use: ui/obj/buddy */

package haven.res.gfx.hud.mmap.plo;

import haven.*;
import haven.res.ui.obj.buddy.*;
import java.util.*;
import java.awt.Color;
import java.awt.image.*;

@haven.FromResource(name = "gfx/hud/mmap/plo", version = 12)
public class Player extends GobIcon.Icon {
	// ND: I have to replace this image manually, rather than the entire "gfx/hud/mmap/plo", cause if I do that, only one player icon is visible in the map icons list. Idk how that works, but this way it works properly.
    public static final Resource.Image img = Resource.local().loadwait("customclient/mapicons/playerIcon").layer(Resource.imgc);
    public final Gob gob = owner.fcontext(Gob.class, false);
    public final int group;
	public final Tex tex;

    public Player(OwnerContext owner, Resource res, int group) {
	super(owner, res);
	this.group = group;
	if(gob != null) {
	    Buddy buddy = gob.getattr(Buddy.class);
	    if((buddy != null) && (buddy.buddy() == null) && (buddy.customName == null))
		throw(new Loading("Waiting for group-info..."));
	}
	// ND: Do this following stuff for the map icon scale setting
	BufferedImage buf = img.img;
	buf = PUtils.rasterimg(PUtils.blurmask2(buf.getRaster(), 1, 1, Color.BLACK));
	Coord tsz;
	if(buf.getWidth() > buf.getHeight())
		tsz = new Coord(GobIcon.size, (GobIcon.size * buf.getHeight()) / buf.getWidth());
	else
		tsz = new Coord((GobIcon.size * buf.getWidth()) / buf.getHeight(), GobIcon.size);
	buf = PUtils.convolve(buf, tsz, GobIcon.filter);
	this.tex = new TexI(buf);

    }

    public Player(OwnerContext owner, Resource res) {
	this(owner, res, -1);
    }

    public int group() {
	Buddy buddy = gob.getattr(Buddy.class);
	if (buddy != null && buddy.customName != null)
		return 0;
	if((buddy != null) && (buddy.buddy() != null))
	    return(buddy.buddy().group);
	return(-1);
    }

    public Object[] id() {
	int grp = (this.group >= 0) ? this.group : group();
	if(grp <= 0)
	    return(nilid);
	return(new Object[grp]);
    }

    public Color color() {
	int grp = group();
	if((grp >= 0) && (grp < BuddyWnd.gc.length))
	    return(BuddyWnd.gc[grp]);
	return(Color.WHITE);
    }

    public String name() {return("Player");}

    public BufferedImage image() {
	if(group < 0)
	    return(img.img);
	BufferedImage buf = PUtils.copy(img.img);
	PUtils.colmul(buf.getRaster(), BuddyWnd.gc[group]);
	return(buf);
    }

    public void draw(GOut g, Coord cc) {
	Color col = Utils.colmul(g.getcolor(), color());
	g.chcolor(col);
	g.rotimage(tex, cc, tex.sz().div(2), -gob.a - (Math.PI * 0.5));
	g.chcolor();
    }

    public boolean checkhit(Coord c) {
	return(c.isect(tex.sz().div(2).inv(), tex.sz()));
    }

    public int z() {return(img.z);}
}
