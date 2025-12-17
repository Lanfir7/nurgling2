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

import java.util.*;
import java.awt.Color;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import haven.resutil.Curiosity;
import static haven.CharWnd.*;
import static haven.PUtils.*;

public class SAttrWnd extends Widget {
    public final Collection<SAttr> attrs;
    private final Coord studyc;
    private CharWnd chr;
    private long scost;
	private static ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private static Future<?> equiporyFuture;

    @RName("sattr")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new SAttrWnd(ui.sess.glob));
	}
    }

    public class SAttr extends CharWnd.AttrWdg {
	public final Tex rnm;
	public final Tex img;
	public final Color bg;
	public int tbv, tcv;
	public long cost;
	private final IButton add, sub;
	private Text ct;
	private int cbv, ccv;
	public final Resource res;
	private Tex baseTex = null;
	private Tex buffedTex = null;

	private SAttr(Glob glob, String attr, Color bg) {
	    super(Coord.of(attrw, attrf.height() + UI.scale(2)), glob, attr);
	    res = Loading.waitfor(this.attr.res());
	    this.img = new TexI(convolve(res.flayer(Resource.imgc).img, new Coord(this.sz.y, this.sz.y), iconfilter));
	    this.rnm = changedSAttrNameTex(res.flayer(Resource.tooltip).t);
	    this.bg = bg;
	    add = adda(new IButton("gfx/hud/buttons/add", "u", "d", "h").action(() -> adj(1)),
		       sz.x, sz.y / 2, 1, 0.5);
	    sub = adda(new IButton("gfx/hud/buttons/sub", "u", "d", "h").action(() -> adj(-1)),
		       add.c.x, sz.y / 2, 1, 0.5);
	}

	public void tick(double dt) {
		if ((attr.base != cbv) || (attr.comp != ccv)) {
		cbv = attr.base;
		ccv = attr.comp;
		if (tbv <= cbv) {
			tbv = cbv;
			tcv = ccv;
			updcost();
		}
		if (ccv > cbv) {
			if (tbv > cbv) {
				buffedTex = PUtils.strokeTex(attrf.render(Integer.toString(ccv + (tbv - cbv)), tbuff));
			} else {
				buffedTex = PUtils.strokeTex(attrf.render(Integer.toString(ccv), buff));
			}
//		    tooltip = String.format("%d + %d", cbv, ccv - cbv);
		} else if (ccv < cbv) {
			if (tbv > cbv) {
				buffedTex = PUtils.strokeTex(attrf.render(Integer.toString(ccv + (tbv - cbv)), tbuff));
			} else {
				buffedTex = PUtils.strokeTex(attrf.render(Integer.toString(ccv), debuff));
			}
//		    tooltip = String.format("%d - %d", cbv, cbv - ccv);
		} else {
			buffedTex = null;
//		    tooltip = null;
		}
		if (tbv > cbv) {
			baseTex = PUtils.strokeTex(attrf.render(Integer.toString(tbv), tbuff));
		} else {
			baseTex = PUtils.strokeTex(attrf.render(Integer.toString(cbv), Color.WHITE));
		}
		}
		updcost();
	}

	public void draw(GOut g) {
	    g.chcolor(bg);
	    g.frect(Coord.z, sz);
	    g.chcolor();
	    super.draw(g);
	    Coord cn = new Coord(0, sz.y / 2);
	    g.aimage(img, cn.add(5, 0), 0, 0.5);
	    g.aimage(rnm, cn.add(img.sz().x + UI.scale(10), 1), 0, 0.6);
		if (buffedTex != null)
			g.aimage(buffedTex, cn.add(sz.x - UI.scale(40), 1), 1, 0.6);
		if (baseTex != null)
			g.aimage(baseTex, cn.add(sz.x - UI.scale(90), 1), 1, 0.6);
	}

	private void updcost() {
		long cost = 100 * ((tbv + ((long) tbv * tbv)) - (attr.base + ((long) attr.base * attr.base))) / 2;
	    scost += cost - this.cost;
	    this.cost = cost;
	}

	public void adj(int a) {
		if (tbv + a < attr.base) a = attr.base - tbv;
	    tbv += a;
		tcv += a;
	    cbv = ccv = 0;
	    updcost();
	}

	public void reset() {
	    tbv = 0;
	    tcv = 0;
		cbv = ccv = 0;
	    updcost();
	}

	public boolean mousewheel(MouseWheelEvent ev) {
	    adj(-ev.a);
	    return(true);
	}

	private Tex changedSAttrNameTex(String t){
		if (t.equals("Unarmed Combat")){
			t = "Unarmed (UA)";
		} else if (t.equals("Melee Combat")) {
			t = "Melee (MC)";
		}
		return PUtils.strokeTex(attrf.render(t));
	}

    }

    public RLabel<?> explabel() {
	return(new RLabel<Integer>(() -> chr.exp, Utils::thformat, new Color(192, 192, 255)));
    }

    public RLabel<?> enclabel() {
	return(new RLabel<Integer>(() -> chr.enc, Utils::thformat, new Color(255, 255, 192)));
    }

    protected void attached() {
	this.chr = getparent(CharWnd.class);
	super.attached();
    }

    public static class StudyInfo extends Widget {
	public final Widget study;
	public int texp, tw, tenc, tlph;

	private StudyInfo(Coord sz, Widget study) {
	    super(sz);
	    this.study = study;
	    Widget plbl, pval;
	    plbl = add(new Label("Attention:"), UI.scale(2, 10));
	    pval = adda(new RLabel<Pair<Integer, Integer>>(() -> new Pair<>(tw, (ui == null) ? 0 : ui.sess.glob.getcattr("int").comp),
							   n -> String.format("%,d/%,d", n.a, n.b),
							   new Color(255, 192, 255, 255)),
				plbl.pos("ur").adds(0, 2).x(sz.x - UI.scale(2)), 1.0, 0.0);
	    plbl = add(new Label("Experience cost:"), pval.pos("bl").adds(0, 2).xs(2));
	    pval = adda(new RLabel<Integer>(() -> tenc, Utils::thformat, new Color(255, 255, 192, 255)), plbl.pos("ur").adds(0, 2).x(sz.x - UI.scale(2)), 1.0, 0.0);
		plbl = add(new Label("Learning points:"), pval.pos("bl").adds(0, 30).xs(2));
	    pval = adda(new RLabel<Integer>(() -> texp, Utils::thformat, new Color(192, 192, 255, 255)), plbl.pos("ur").adds(0, 2).x(sz.x - UI.scale(2)), 1.0, 0.0);
		plbl = add(new Label("LP/Hour:"), pval.pos("bl").adds(0, 2).xs(2));
		pval = adda(new RLabel<Integer>(() -> tlph, Utils::thformat, new Color(192, 255, 255, 255)), plbl.pos("ur").adds(0, 2).x(sz.x - UI.scale(2)), 1.0, 0.0);
	}

	private void upd() {
	    int texp = 0, tw = 0, tenc = 0,  tlph = 0;
	    for(GItem item : study.children(GItem.class)) {
		try {
		    Curiosity ci = ItemInfo.find(Curiosity.class, item.info());
		    if(ci != null) {
			texp += ci.exp;
			tw += ci.mw;
			tenc += ci.enc;
			tlph += ci.lph;
		    }
		} catch(Loading l) {
		}
	    }
	    this.texp = texp; this.tw = tw; this.tenc = tenc; this.tlph = tlph;
	}

	public void tick(double dt) {
	    upd();
	    super.tick(dt);
	}
    }

    private void buy() {
	ArrayList<Object> args = new ArrayList<>();
	for (SAttr attr : attrs) {
	    if (attr.tbv > 0) {
			args.add(attr.attr.nm);
			args.add(attr.tbv);
		}
	}
	wdgmsg("sattr", args.toArray(new Object[0]));
	if (equiporyFuture != null)
		equiporyFuture.cancel(true);
	// ND: Hopefully 500ms is enough
	equiporyFuture = executor.scheduleWithFixedDelay(this::resetEquiporyBottomText, 500, 5000, TimeUnit.MILLISECONDS);
    }

    private void reset() {
	for (SAttr attr : attrs)
	    attr.reset();
    }

    public SAttrWnd(Glob glob) {
	Widget prev;
	prev = add(CharWnd.settip(new Img(catf.render("Abilities").tex()), "gfx/hud/chr/tips/sattr"), Coord.z);
	attrs = new ArrayList<>();
	SAttr aw;
	attrs.add(aw = add(new SAttr(glob, "unarmed", every), prev.pos("bl").adds(5, 0).add(wbox.btloff())));
	attrs.add(aw = add(new SAttr(glob, "melee", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "ranged", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "explore", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "stealth", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "sewing", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "smithing", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "masonry", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "carpentry", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "cooking", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "farming", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "survive", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "lore", every), aw.pos("bl")));
	Widget lframe = Frame.around(this, attrs);

	prev = add(CharWnd.settip(new Img(catf.render("Study Report").tex()), "gfx/hud/chr/tips/study"), width, 0);
	studyc = prev.pos("bl").adds(5, 0);
	Widget bframe = adda(new Frame(new Coord(attrw, UI.scale(105)), true), prev.pos("bl").adds(5, 0).x, lframe.pos("br").y, 0.0, 1.0);
	int rx = bframe.pos("iur").subs(10, 0).x;
	prev = add(new Label("Experience points:"), bframe.pos("iul").adds(10, 5));
	adda(enclabel(), new Coord(rx, prev.pos("ul").y), 1.0, 0.0);
	prev = add(new Label("Learning points:"), prev.pos("bl").adds(0, 2));
	adda(explabel(), new Coord(rx, prev.pos("ul").y), 1.0, 0.0);
	prev = add(new Label("Learning cost:"), prev.pos("bl").adds(0, 2));
	adda(new RLabel<Long>(() -> scost, Utils::thformat, n -> (n > chr.exp) ? debuff : Color.WHITE), new Coord(rx, prev.pos("ul").y), 1.0, 0.0);
	prev = adda(new Button(UI.scale(75), "Buy").action(this::buy), bframe.pos("ibr").subs(5, 5), 1.0, 1.0);
	adda(new Button(UI.scale(75), "Reset").action(this::reset), prev.pos("bl").subs(5, 0), 1.0, 1.0);
	pack();
    }

    public void addchild(Widget child, Object... args) {
	String place = (args[0] instanceof String) ? (((String)args[0]).intern()) : null;
	if(place == "study") {
	    add(child, studyc.add(wbox.btloff()));
	    Widget f = Frame.around(this, Collections.singletonList(child));
		Widget prevStudyCheckbox;
		prevStudyCheckbox = add(showStudyReportHistoryCheckBox, child.pos("bl").adds(0, 11));
		prevStudyCheckbox.tooltip = OptWnd.showStudyReportHistoryTooltip;
		prevStudyCheckbox = add(lockStudyReportCheckBox, prevStudyCheckbox.pos("bl").adds(0, 2));
		prevStudyCheckbox.tooltip = OptWnd.lockStudyReportTooltip;
		prevStudyCheckbox = add(soundAlertForFinishedCuriositiesCheckBox, prevStudyCheckbox.pos("bl").adds(0, 2));
		prevStudyCheckbox.tooltip = OptWnd.soundAlertForFinishedCuriositiesTooltip;
		prevStudyCheckbox = add(autoReloadCuriositiesFromInventoryCheckBox, prevStudyCheckbox.pos("bl").adds(0, 2));
		prevStudyCheckbox.tooltip = OptWnd.autoReloadCuriositiesFromInventoryTooltip;
	    Widget inf = add(new StudyInfo(new Coord(attrw - child.sz.x - wbox.bisz().x - UI.scale(5), child.sz.y), child), child.pos("ur").add(wbox.bisz().x + UI.scale(5), 0));
	    Frame.around(this, Collections.singletonList(inf));
		getparent(GameUI.class).miniStudy.setStudy((Inventory) child);
	    pack();
	} else {
	    super.addchild(child, args);
	}
    }

	public void resetEquiporyBottomText() {
		if (ui != null && ui.gui != null && ui.gui.getequipory() != null){
			ui.gui.getequipory().updateBottomText = true;
			ui.gui.getequipory().delayedUpdateTime = System.currentTimeMillis();
		}
		equiporyFuture.cancel(true);
	}

	public static CheckBox showStudyReportHistoryCheckBox = new CheckBox("Show Study Report History"){
		{a = OptWnd.showStudyReportHistoryCheckBox.a;}
		public void set(boolean val) {
			OptWnd.showStudyReportHistoryCheckBox.set(val);
			a = val;
		}
	};
	public static CheckBox lockStudyReportCheckBox = new CheckBox("Lock Study Report"){
		{a = OptWnd.lockStudyReportCheckBox.a;}
		public void set(boolean val) {
			OptWnd.lockStudyReportCheckBox.set(val);
			a = val;
		}
	};
	public static CheckBox soundAlertForFinishedCuriositiesCheckBox = new CheckBox("Sound Alert for Finished Curiosities"){
		{a = OptWnd.soundAlertForFinishedCuriositiesCheckBox.a;}
		public void set(boolean val) {
			OptWnd.soundAlertForFinishedCuriositiesCheckBox.set(val);
			a = val;
		}
	};
	public static CheckBox autoReloadCuriositiesFromInventoryCheckBox = new CheckBox("Auto-Reload Curiosities from Inventory"){
		{a = OptWnd.autoReloadCuriositiesFromInventoryCheckBox.a;}
		public void set(boolean val) {
			OptWnd.autoReloadCuriositiesFromInventoryCheckBox.set(val);
			a = val;
		}
	};
}
