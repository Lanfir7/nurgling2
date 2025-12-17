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

package haven.resutil;

import haven.*;
import java.util.*;
import java.awt.Color;
import java.awt.image.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static haven.CharWnd.iconfilter;
import static haven.PUtils.convolve;

public class FoodInfo extends ItemInfo.Tip {
    public final double end, glut, sev, cons;
    public final Event[] evs;
    public final Effect[] efs;
    public final int[] types;
	private UI ui = null;

    public FoodInfo(Owner owner, double end, double glut, double cons, double sev, Event[] evs, Effect[] efs, int[] types) {
	super(owner);
	this.end = end;
	this.glut = glut;
	this.sev = sev;
	this.cons = cons;
	this.evs = evs;
	this.efs = efs;
	this.types = types;
	if (owner instanceof GItem){
		this.ui = ((GItem) owner).ui;
	}
    }

    public FoodInfo(Owner owner, double end, double glut, double cons, Event[] evs, Effect[] efs, int[] types) {
	this(owner, end, glut, cons, 0, evs, efs, types);
    }

    public static class Event {
	public static final Coord imgsz = new Coord(Text.std.height(), Text.std.height());
	public final BAttrWnd.FoodMeter.Event ev;
	public final BufferedImage img;
	public final double a;

	public Event(Resource res, double a) {
	    this.ev = res.flayer(BAttrWnd.FoodMeter.Event.class);
	    this.img = PUtils.convolve(res.flayer(Resource.imgc).img, imgsz, CharWnd.iconfilter);
	    this.a = a;
	}
    }

    public static class Effect {
	public final List<ItemInfo> info;
	public final double p;

	public Effect(List<ItemInfo> info, double p) {this.info = info; this.p = p;}
    }

	// TODO: ND: This method needs to be cleaned up
    public BufferedImage tipimg() {
		String head = null;
		double hungerEfficiency = 100;
		double fepEfficiency = 100;
		double satiation = 1;
		boolean calculateEfficiency = ui != null && !ui.modshift;
		double tableFoodEventBonus = 1.0;
		boolean isSalted = (owner instanceof GItem && ((GItem) owner).isSalted());
		double saltSatiation = 1;
		Window feastingWindow = null;
		if (ui != null && ui.gui != null && ui.gui.chrwdg != null && ui.gui.chrwdg.battr != null && ui.gui.chrwdg.battr.cons != null && ui.gui.chrwdg.battr.glut != null) {
			for(int i = 0; i < ui.gui.chrwdg.battr.cons.els.size(); i++) {
				BAttrWnd.Constipations.El el = ui.gui.chrwdg.battr.cons.els.get(i);
				for (int type : types) {
					if (type == i) {
						satiation = (1.0 - el.a);
						hungerEfficiency = fepEfficiency *= satiation;
						break;
					}
				}
				if (isSalted && el.t.res.get().basename().equals("salt")) {
					saltSatiation = (1.0 - el.a);
					hungerEfficiency = fepEfficiency *= saltSatiation;
				}
			}
			fepEfficiency *= ui.gui.chrwdg.battr.glut.gmod;
			if (GameUI.subscribedAccount && GameUI.verifiedAccount) fepEfficiency *= 1.5;
			else if (GameUI.subscribedAccount) fepEfficiency *= 1.3;
			else if (GameUI.verifiedAccount) fepEfficiency *= 1.2;
			outerLoop:
			for (Window wnd : ui.gui.getAllWindows()) {
				if (wnd.cap.equals("Table")) {
					for (Widget wdg : wnd.children()) {
						if (wdg instanceof Button) {
							feastingWindow = wnd;
							break outerLoop; // Break out of both loops
						}
					}
				}
			}
			if (feastingWindow != null) {
				for(Widget wdg : feastingWindow.children()) {
					if (wdg instanceof Label) {
						String labelString = ((Label)wdg).texts;
						if (labelString.startsWith("Food event bonus")){
							tableFoodEventBonus = (extractNumber(labelString) > 0.0) ? 1.0 + (extractNumber(labelString)/100) : 1.0;
							fepEfficiency *= tableFoodEventBonus;
						}
						else if (labelString.startsWith("Hunger modifier")) {
							hungerEfficiency *= (extractNumber(labelString) < 100 && extractNumber(labelString) > 0.0) ? (extractNumber(labelString)/100) : 1.0;
						}
					}
				}
			}
			boolean deepWorm = false;
			if (ui != null && ui.gui != null && ui.gui.chrwdg != null && ui.gui.chrwdg.wound != null && ui.gui.chrwdg.wound.wounds != null && ui.gui.chrwdg.wound.wounds.wounds != null) {
				for (WoundWnd.Wound wound : ui.gui.chrwdg.wound.wounds.wounds) {
					if (wound != null && wound.res != null && wound.res.get() != null && wound.res.get().name != null && wound.res.get().name.equals("paginae/wound/deepworm")) {
						deepWorm = true;
						break;
					}
				}
			}
			if (deepWorm) {
				head = "\n$col[255,25,25]{HEY! You have a Deep Worm Wound!" +
						"\nYour food efficiency will be reduced!" +
						"\nThe following values are inaccurate!}" +
						"\n";
				head += String.format("\nFood Efficiency: $col[49,255,39]{%s%%}", Utils.odformat2(calculateEfficiency ? fepEfficiency : 100, 2));
			} else
				head = String.format("\nFood Efficiency: $col[49,255,39]{%s%%}", Utils.odformat2(calculateEfficiency ? fepEfficiency : 100, 2));
		}
		else
			head = "";
		head += String.format("\nEnergy: $col[128,128,255]{%s%%}  |  Hunger: $col[255,192,128]{%s\u2030}", Utils.odformat2(end * 100, 2), Utils.odformat2(calculateEfficiency ? (glut * 1000 * (hungerEfficiency/100)) : (glut * 1000), 2));
		double totalFeps = 0;
		for (int i = 0; i < evs.length; i++) {
			totalFeps += evs[i].a;
		}
		if (evs.length > 0) {
			head += "\n\nFood Event Points:";
		}
	BufferedImage base = RichText.render(head, 0).img;
	Collection<BufferedImage> imgs = new LinkedList<BufferedImage>();
	imgs.add(base);
	for(int i = 0; i < evs.length; i++) {
	    Color col = Utils.blendcol(evs[i].ev.col, Color.WHITE, 0.5);
		imgs.add(catimgsh(5, UI.scale(15), null, evs[i].img, RichText.render(String.format("%s: $col[%d,%d,%d]{%s} - %s%%", evs[i].ev.nm, col.getRed(), col.getGreen(), col.getBlue(), Utils.odformat2(calculateEfficiency ? (evs[i].a * (fepEfficiency/100)) : evs[i].a, 2), Utils.odformat2(((evs[i].a/totalFeps)*100), 2)), 0).img));
	}
	for(int i = 0; i < efs.length; i++) {
	    BufferedImage efi = ItemInfo.longtip(efs[i].info);
	    if(efs[i].p != 1)
		efi = catimgsh(5, efi, RichText.render(String.format("$i{($col[192,192,255]{%d%%} chance)}", (int)Math.round(efs[i].p * 100)), 0).img);
	    imgs.add(efi);
	}
		imgs.add(RichText.render(String.format("\n(+2 Weighted) Total FEPs: $col[0,180,0]{%s}", Utils.odformat2(calculateEfficiency ? (sev * (fepEfficiency/100)) : sev, 2)), 0).img);
		if (glut > 0) {
			imgs.add(RichText.render(String.format("(+2 Weighted) FEPs/Hunger: $col[0,180,0]{%s}", Utils.odformat2(calculateEfficiency ? sev * (fepEfficiency / 100) / (glut * 1000 * (hungerEfficiency / 100)) : sev / (glut * 1000), 2)), 0).img);
		}
		imgs.add(RichText.render(String.format("\n(Actual) Total FEPs: $col[0,180,0]{%s}", Utils.odformat2(calculateEfficiency ? (totalFeps * (fepEfficiency/100)) : totalFeps, 2)), 0).img);
		if (glut > 0) {
			imgs.add(RichText.render(String.format("(Actual) FEPs/Hunger: $col[0,180,0]{%s}", Utils.odformat2(calculateEfficiency ? totalFeps * (fepEfficiency / 100) / (glut * 1000 * (hungerEfficiency / 100)) : totalFeps / (glut * 1000), 2)), 0).img);
		}
		if (calculateEfficiency && ui != null && ui.gui != null && ui.gui.chrwdg != null && ui.gui.chrwdg.battr != null && ui.gui.chrwdg.battr.feps != null && ui.gui.chrwdg.battr.feps.els != null){
			List<BAttrWnd.FoodMeter.El> els = ui.gui.chrwdg.battr.feps.els;
			BufferedImage cur = null;
			double currentFEPs = 0.0;
			for(BAttrWnd.FoodMeter.El el : els) {
				BAttrWnd.FoodMeter.Event ev = el.res.get().flayer(BAttrWnd.FoodMeter.Event.class);
				Color col = Utils.blendcol(ev.col, Color.WHITE, 0.5);
				BufferedImage ln = Text.render(String.format("%s: %s", ev.nm, Utils.odformat2(el.a, 2)), col).img;
				Resource.Image icon = el.res.get().layer(Resource.imgc);
				if(icon != null)
					ln = ItemInfo.catimgsh(5, convolve(icon.img, new Coord(ln.getHeight(), ln.getHeight()), iconfilter), ln);
				cur = ItemInfo.catimgs(0, cur, ln);
				currentFEPs += el.a;
			}
			double fill = ((currentFEPs + (totalFeps * (fepEfficiency/100)))/ui.gui.chrwdg.battr.feps.cap * 100);
			imgs.add(RichText.render(String.format("\nThis will fill your FEP bar to: " + (fill > 100 ? "$col[0,180,0]" : "$col[252,186,3]")  + "{%s%%}", Utils.odformat2(fill, 2)), 0).img);
		}

		imgs.add(RichText.render(calculateEfficiency ? "\n$col[185,185,185]{<Hold Shift to Hide Modifiers>}" : "\n$col[218,163,0]{<Showing Unmodified Values>}", 300).img);
		if (calculateEfficiency && ui != null && ui.gui != null && ui.gui.chrwdg != null && ui.gui.chrwdg.battr != null && ui.gui.chrwdg.battr.glut != null) {
			if (GameUI.subscribedAccount && GameUI.verifiedAccount) imgs.add(RichText.render("x 1.5 - $col[185,185,185]{Verified} and $col[185,185,185]{Subscribed}", 300).img);
			else if (GameUI.subscribedAccount) imgs.add(RichText.render("x 1.3 - $col[185,185,185]{Subscribed}", 300).img);
			else if (GameUI.verifiedAccount) imgs.add(RichText.render("x 1.2 - $col[185,185,185]{Verified}", 300).img);
			imgs.add(RichText.render(String.format("x %s - $col[185,185,185]{FEP Multiplier (Hunger Bar)}", Utils.odformat2(ui.gui.chrwdg.battr.glut.gmod, 2)), 300).img);
			imgs.add(RichText.render(String.format("x %s - $col[185,185,185]{Satiation}", Utils.odformat2(satiation, 2)), 300).img);
			if (isSalted)
			imgs.add(RichText.render(String.format("x %s - $col[185,185,185]{Salt Satiation}", Utils.odformat2(saltSatiation, 2)), 300).img);
			if (feastingWindow != null) {
				imgs.add(RichText.render(String.format("x %s - $col[185,185,185]{Table Food Event Bonus}", Utils.odformat2(tableFoodEventBonus, 2)), 300).img);
			}
		}

	return(catimgs(0, imgs.toArray(new BufferedImage[0])));
    }

	private static double extractNumber(String str) {
		Pattern pattern = Pattern.compile("\\d+");
		Matcher matcher = pattern.matcher(str);

		if (matcher.find()) {
			String number = matcher.group();
			return Double.parseDouble(number); // Convert to double
		}

		return 0; // Return 1.0
	}
}
