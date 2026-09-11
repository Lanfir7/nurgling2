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

import nurgling.*;

import java.awt.Color;
import java.util.*;

public class IMeter extends LayerMeter {
	public String name;
	Tex text = null;
		// Live SHP/HHP/max from the "hp" meter's tip uimsg (drawn on the bar, not hover-only).
		// Meter.a cannot tell soft damage from a reduced hard-HP ceiling.
		public int curHealth = -1, hardHealth = -1, maxHealth = -1;

		/** Parsed SHP/HHP/max from a Health tip body such as "80/120/150". */
		public static final class HealthNumbers {
			public final int soft;
			public final int hard;
			public final int max;
			public final boolean sparring;

			HealthNumbers(int soft, int hard, int max, boolean sparring) {
				this.soft = soft;
				this.hard = hard;
				this.max = max;
				this.sparring = sparring;
			}

			public double hardFraction() {
				return IMeter.hardFraction(hard, max);
			}
		}

		/** HHP / max, or -1 if either value is missing. */
		public static double hardFraction(int hardHealth, int maxHealth) {
			if(hardHealth < 0 || maxHealth <= 0)
				return -1;
			return (double) hardHealth / (double) maxHealth;
		}

		/** Health tip is "SHP/HHP/Max" or "SHP/HHP/Max/sparring". */
		public static HealthNumbers parseHealthNumbers(String value) {
			if(value == null)
				return null;
			String[] hps = value.replaceAll("\\(.+\\)", "").split("/");
			if(hps.length < 3)
				return null;
			try {
				int soft = (int) Math.round(Double.parseDouble(hps[0].trim()));
				int hard = (int) Math.round(Double.parseDouble(hps[1].trim()));
				int max = (int) Math.round(Double.parseDouble(hps[hps.length - 1].trim()));
				return new HealthNumbers(soft, hard, max, hps.length == 4);
			} catch(NumberFormatException e) {
				return null;
			}
		}

	public static String characterCurrentHealth = "";
	public static double characterSoftHealthPercent = 0;
	public static boolean sparring = false;
	public String currentHealth = "";
	public double softHealthPercent = 0;
	public boolean isSparring = false;
    public static final Coord off = UI.scale(24, 4);
    public static final Coord fsz = UI.scale(190, 48);
    public static final Coord ssz = UI.scale(145, 48);
    public static final Coord msz = UI.scale(130, 20);
    public final Indir<Resource> bg;

    @RName("im")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    Indir<Resource> bg = ui.sess.getresv(args[0]);
        String resnm = ui.sess.rescache.get((Integer)args[0]).resnm;
        if(resnm!=null)
        {
            String key = resnm.substring(resnm.lastIndexOf("/")+1);
            switch (key)
            {
                case "hp":
                    bg = Resource.remote().load("nurgling/hud/meter/hp");
                    break;
                case "stam":
                    bg = Resource.remote().load("nurgling/hud/meter/stam");
                    break;
                case "nrj":
                    bg = Resource.remote().load("nurgling/hud/meter/nrj");
                    break;
                case "mount":
                    bg = Resource.remote().load("nurgling/hud/meter/mount");
                    break;
                case "boat":
                    bg = Resource.remote().load("nurgling/hud/meter/boat");
                    break;
                case "häst":
                    bg = Resource.remote().load("nurgling/hud/meter/hast");
                    break;
            }
        }

	    List<Meter> meters = decmeters(args, 1);
		IMeter result = new IMeter(bg, meters);
		result.name = resnm;
		if(resnm!= null && result.name.endsWith("st"))
			result.name = "gfx/hud/meter/hast";
	    return(result);
	}
    }

    public IMeter(Indir<Resource> bg, List<Meter> meters) {
	super(fsz);
	this.bg = bg;
	set(meters);
    }

	@Override
	public void set(List<Meter> meters) {
		super.set(meters);
		PonyPowerAlert.onUpdate(name, meters);
	}

    public void draw(GOut g) {
	try {
	    Tex bg = this.bg.get().flayer(Resource.imgc).tex();
	    g.chcolor(0, 0, 0, 255);
	    g.frect(off, msz);
	    g.chcolor();
	    for(Meter m : meters) {
		int w = msz.x;
		w = (int)Math.ceil(w * m.a);
		g.chcolor(m.c);
		g.frect(off, new Coord(w, msz.y));
	    }
	    g.chcolor();
	    g.image(bg, Coord.z);
		if(text!=null)
		{
			g.image(text,new Coord(off.x + msz.x/2 -text.sz().x/2,off.y + msz.y/2 -text.sz().y/2));
		}
	} catch(Loading l) {
	}
    }

	static String tipKey(String val) {
		if(val == null)
			return null;
		int colon = val.indexOf(':');
		if(colon < 0)
			return null;
		return val.substring(0, colon).trim();
	}

	static String tipValue(String val) {
		if(val == null)
			return null;
		int colon = val.indexOf(':');
		if(colon < 0)
			return null;
		return val.substring(colon + 1);
	}

	static boolean meterName(String key, String english, String l10nKey) {
		if(key == null || key.isEmpty())
			return false;
		if(english.equals(key))
			return true;
		return l10nKey != null && nurgling.i18n.L10n.get(l10nKey).equals(key);
	}

	@Override
	public void uimsg(String msg, Object... args)
	{
		if(msg == "tip") {
			String val = (String) args[0];
			String key = tipKey(val);
			String rest = tipValue(val);
			if(key != null && rest != null)
			{
				if(meterName(key, "Health", "widget.hp")) {
					parseHealth(rest);
					text = NStyle.meter.render(rest.replace("/", " / ")).tex();
				} else if(meterName(key, "Energy", "widget.energy")) {
					int pct = rest.lastIndexOf('%');
					text = NStyle.meter.render(pct >= 0 ? rest.substring(0, pct + 1) : rest).tex();
				} else if(meterName(key, "Stamina", "widget.stam")
					|| "Satiety".equals(key)
					|| "Pony Power".equals(key)
					|| "Seaworthiness".equals(key))
					text = NStyle.meter.render(rest).tex();
			}
		}
		super.uimsg(msg, args);
	}

	private void parseHealth(String value) {
		HealthNumbers nums = parseHealthNumbers(value);
		if(nums == null)
			return;
		isSparring = nums.sparring;
		curHealth = nums.soft;
		hardHealth = nums.hard;
		maxHealth = nums.max;
		softHealthPercent = (nums.soft > 0 && nums.max > 0) ? (nums.soft / (nums.max / 100.0)) : 0;
		currentHealth = nums.soft + " / " + nums.max;
		sparring = isSparring;
		characterSoftHealthPercent = softHealthPercent;
		characterCurrentHealth = currentHealth;
	}
}
