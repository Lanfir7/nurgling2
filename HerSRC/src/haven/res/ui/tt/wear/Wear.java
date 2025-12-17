/* Preprocessed source code */
package haven.res.ui.tt.wear;

import haven.*;
import java.awt.image.BufferedImage;

/* >tt: Wear */
@haven.FromResource(name = "ui/tt/wear", version = 4)
public class Wear extends ItemInfo.Tip {
    public final int d, m;
    public final double percentage;

    public Wear(Owner owner, int d, int m) {
	super(owner);
	this.d = d;
	this.m = m;
    this.percentage = ((m-d)/(m/100d));
    }

    public static ItemInfo mkinfo(Owner owner, Object... args) {
	return(new Wear(owner, (Integer)args[1], (Integer)args[2]));
    }

    public BufferedImage tipimg() {
	if(d >= m)
	    return(RichText.render(String.format("$col[70,194,80]{Durability}: $col[255,128,128]{%,d/%,d} $col[255,255,255]{(%,.1f%%)}", (m-d), m, percentage), 0).img);
	return(RichText.render((String.format("$col[70,194,80]{Durability}: %,d/%,d (%,.2f%%)", (m-d), m, percentage)), 0).img);
    }

    public void layout(Layout l) {
        BufferedImage t = tipimg(l.width);
        if(t != null)
            l.cmp.add(t, new Coord(0, l.cmp.sz.y + UI.scale(4)));
    }
}
