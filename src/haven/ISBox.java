/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.awt.Color;
import nurgling.*;
import nurgling.db.StockpileStoragePolicy;

public class ISBox extends Widget implements DTarget {
    public static final Color bgcol = new Color(43, 51, 44, 127);
    public static final IBox box = new IBox.Scaled("gfx/hud/bosq", "tl", "tr", "bl", "br", "el", "er", "et", "eb") {
	    public void draw(GOut g, Coord tl, Coord sz) {
		super.draw(g, tl, sz);
		g.chcolor(bgcol);
		g.frect(tl.add(ctloff()), sz.sub(cisz()));
		g.chcolor();
	    }
	};
    public static final Coord defsz = UI.scale(145, 42);
    public static final Text.Foundry lf = new Text.Foundry(Text.fraktur, 22, Color.WHITE).aa(true);
    private final Indir<Resource> res;
    protected Text label;
    protected int rem;
    protected int av;
    protected int bi;

    @RName("isbox")
    public static class $_ implements Factory {
		public Widget create(UI ui, Object[] args) {
		    Indir<Resource> res;
		    if(args[0] instanceof String)
			res = Resource.remote().load((String)args[0]);
		    else
			res = ui.sess.getresv(args[0]);
		    NISBox box = new NISBox(res, Utils.iv(args[1]), Utils.iv(args[2]), Utils.iv(args[3]));
		    if(ui.core.getLastActions() != null) {
			box.parentGob = ui.core.getLastActions().gob;
		    }
		    return box;
		}
    }

    private void setlabel(int rem, int av, int bi) {
	this.rem = rem;
	this.av = av;
	this.bi = bi;
	if(bi < 0)
	    label = lf.renderf("%d/%d", rem, av);
	else
	    label = lf.renderf("%d/%d/%d", rem, av, bi);
    }

    public ISBox(Indir<Resource> res, int rem, int av, int bi) {
	super(defsz);
	this.res = res;
	setlabel(rem, av, bi);
	tooltip = new PaginaTip(res, true);
    }

    public void draw(GOut g) {
	box.draw(g, Coord.z, sz);
	try {
            Tex t = res.get().flayer(Resource.imgc).tex();
            Coord dc = Coord.of(UI.scale(6), (sz.y - t.sz().y) / 2);
            g.image(t, dc);
        } catch(Loading e) {}
        g.image(label.tex(), new Coord(UI.scale(40), (sz.y - label.sz().y) / 2));
    }

    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b == 1) {
	    beginTransfer(StockpileStoragePolicy.TransferDirection.OUT_OF_PILE);
            if(ui.modshift)
                wdgmsg("xfer");
            else
                wdgmsg("click");
            return(true);
        }
        return(super.mousedown(ev));
    }

    public boolean mousewheel(MouseWheelEvent ev) {
		if(ev.a < 0) {
		    beginTransfer(StockpileStoragePolicy.TransferDirection.OUT_OF_PILE);
		    wdgmsg("xfer2", -1, ui.modflags());
		}
		if(ev.a > 0) {
		    beginTransfer(StockpileStoragePolicy.TransferDirection.INTO_PILE);
		    wdgmsg("xfer2", 1, ui.modflags());
		}
	return(true);
    }

    public boolean drop(Coord cc, Coord ul) {
	        beginTransfer(StockpileStoragePolicy.TransferDirection.INTO_PILE);
        wdgmsg("drop");
        return(true);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
	        beginTransfer(StockpileStoragePolicy.TransferDirection.INTO_PILE);
        wdgmsg("iact");
        return(true);
    }

    public void uimsg(String msg, Object... args) {
	        if(msg == "chnum") {
	            int oldCount = rem;
	            setlabel(Utils.iv(args[0]), Utils.iv(args[1]), Utils.iv(args[2]));
	            Gob gob = parentPileGob();
	            if(gob != null)
	                monitoring.StockpileStorageTracker.onPileCountChanged(gob, oldCount, rem);
        } else {
            super.uimsg(msg, args);
        }
    }

	    protected Gob parentPileGob() {
		return (this instanceof NISBox) ? ((NISBox) this).parentGob : null;
	    }

	    public int stockpileCount() {
		return rem;
	    }

	    public String stockpileItemName() {
		try {
		    Resource.Tooltip tip = res.get().layer(Resource.tooltip);
		    return tip == null ? null : tip.text();
		} catch(Loading e) {
		    return null;
		}
	    }

	    public void beginDepositTracking() {
		beginTransfer(StockpileStoragePolicy.TransferDirection.INTO_PILE);
	    }

	    public void beginWithdrawalTracking() {
		beginTransfer(StockpileStoragePolicy.TransferDirection.OUT_OF_PILE);
	    }

	    private void beginTransfer(StockpileStoragePolicy.TransferDirection direction) {
		Gob gob = parentPileGob();
		String name = stockpileItemName();
		if(gob != null && name != null)
		    monitoring.StockpileStorageTracker.beginTransfer(gob, name, direction, rem);
	    }
}
