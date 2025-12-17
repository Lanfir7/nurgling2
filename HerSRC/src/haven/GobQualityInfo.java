package haven;

import haven.render.Homo3D;
import haven.render.Pipe;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class GobQualityInfo extends GobInfo {
    public static Pattern GOB_Q = Pattern.compile("Quality: (\\d+)");
    private static final Map<Long, Integer> gobQ = new LinkedHashMap<Long, Integer>() {
	@Override
	protected boolean removeEldestEntry(Map.Entry eldest) {
	    return size() > 50;
	}
    };
    int q;

    protected GobQualityInfo(Gob owner) {
	super(owner);
	q = gobQ.getOrDefault(gob.id, 0);
    }
    
    
    public void setQ(int q) {
	gobQ.put(gob.id, q);
	this.q = q;
    }
    
    @Override
	protected boolean enabled() {
		return OptWnd.displayObjectQualityOnInspectionCheckBox.a;
	}

    @Override
    protected Tex render() {
	if(gob == null || gob.getres() == null) { return null;}

	BufferedImage quality = quality();

	if(quality == null) {
	    return null;
	}

	return new TexI(ItemInfo.catimgsh(3, 0, null, quality));
    }
    
    @Override
    public void dispose() {
	super.dispose();
    }

    private BufferedImage quality() {
	if(q != 0) {
		return Text.renderstroked(String.format("Q: %d", q), Color.white, Color.BLACK, Text.num12boldFnd).img;
	}
	return null;
    }

    @Override
    public String toString() {
	Resource res = gob.getres();
	return String.format("GobInfo<%s>", res != null ? res.name : "<loading>");
    }

	@Override
	public void draw(GOut g, Pipe state) {
		synchronized (texLock) {
			if(enabled() && tex != null) {
				Coord sc = Homo3D.obj2sc(pos, state, Area.sized(g.sz()));
				if(sc == null) {return;}
				sc.y = sc.y + UI.scale(4);
				if(sc.isect(Coord.z, g.sz())) {
					g.aimage(tex, sc, center.a, center.b);
				}
			}
		}
	}

}