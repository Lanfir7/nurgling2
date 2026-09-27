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

public class FoodInfo extends ItemInfo.Tip {
    public final double end, glut, sev, cons;
    public final Event[] evs;
    public final Effect[] efs;
    public final int[] types;

    public FoodInfo(Owner owner, double end, double glut, double cons, double sev, Event[] evs, Effect[] efs, int[] types) {
	super(owner);
	this.end = end;
	this.glut = glut;
	this.sev = sev;
	this.cons = cons;
	this.evs = evs;
	this.efs = efs;
	this.types = types;
    }

    public FoodInfo(Owner owner, double end, double glut, double cons, Event[] evs, Effect[] efs, int[] types) {
	this(owner, end, glut, cons, 0, evs, efs, types);
    }

    public static class Event {
	public static final Coord imgsz = new Coord(Text.std.height(), Text.std.height());
	private static final EventImageCache images = new EventImageCache(128, 2L * 1024 * 1024);
	public final BAttrWnd.FoodMeter.Event ev;
	public final BufferedImage img;
	public final double a;

	public Event(Resource res, double a) {
	    this.ev = res.flayer(BAttrWnd.FoodMeter.Event.class);
	    this.img = images.get(res.flayer(Resource.imgc).img, imgsz);
	    this.a = a;
	}

	public Event(BAttrWnd.FoodMeter.Event ev, BufferedImage img, double a) {
		this.ev = ev;
		this.img = img;
		this.a = a;
	}
    }

    /** Only immutable resource pixels are shared; no texture or event state is cached. */
    static final class EventImageCache {
	private final int maxEntries;
	private final long maxPixels;
	private long pixels;
	private final LinkedHashMap<ImageKey, BufferedImage> entries = new LinkedHashMap<>(16, 0.75f, true);

	EventImageCache(int maxEntries, long maxPixels) {
	    this.maxEntries = maxEntries;
	    this.maxPixels = maxPixels;
	}

	BufferedImage get(BufferedImage source, Coord size) {
	    ImageKey key = new ImageKey(source, size.x, size.y);
	    synchronized(this) {
		BufferedImage found = entries.get(key);
		if(found != null)
		    return(found);
	    }
	    BufferedImage image = PUtils.convolve(source, new Coord(key.width, key.height), CharWnd.iconfilter);
	    // Include source pixels retained by the identity key in the memory budget.
	    long cost = key.pixels();
	    if(maxEntries <= 0 || cost > maxPixels)
		return(image);
	    synchronized(this) {
		BufferedImage found = entries.get(key);
		if(found != null)
		    return(found);
		while(!entries.isEmpty() && (entries.size() >= maxEntries || pixels + cost > maxPixels)) {
		    Iterator<ImageKey> oldest = entries.keySet().iterator();
		    pixels -= oldest.next().pixels();
		    oldest.remove();
		}
		entries.put(key, image);
		pixels += cost;
	    }
	    return(image);
	}

	private static final class ImageKey {
	    final BufferedImage source;
	    final int width, height;

	    ImageKey(BufferedImage source, int width, int height) {
		this.source = source;
		this.width = width;
		this.height = height;
	    }

	    long pixels() {
		return((long) source.getWidth() * source.getHeight() + (long) width * height);
	    }

	    public int hashCode() {
		return((System.identityHashCode(source) * 31 + width) * 31 + height);
	    }

	    public boolean equals(Object other) {
		if(!(other instanceof ImageKey))
		    return(false);
		ImageKey key = (ImageKey)other;
		return(source == key.source && width == key.width && height == key.height);
	    }
	}
    }

    public static class Effect {
	public final List<ItemInfo> info;
	public final double p;

	public Effect(List<ItemInfo> info, double p) {this.info = info; this.p = p;}
    }

    public void layout(Layout l) {
	String head = String.format("Energy: $col[128,128,255]{%s%%}, Hunger: $col[255,192,128]{%s\u2030}", Utils.odformat2(end * 100, 2), Utils.odformat2(glut * 1000, 2));
	if(cons != 0)
	    head += String.format(", Satiation: $col[192,192,128]{%s%%}", Utils.odformat2(cons * 100, 2));
	l.cmp.add(RichText.render(head, 0).img, Coord.of(0, l.cmp.sz.y));
	for(int i = 0; i < evs.length; i++) {
	    Color col = Utils.blendcol(evs[i].ev.col, Color.WHITE, 0.5);
	    l.cmp.add(catimgsh(5, evs[i].img, RichText.render(String.format("%s: %s{%s}", evs[i].ev.nm, RichText.Parser.col2a(col), Utils.odformat2(evs[i].a, 2)), 0).img),
		      Coord.of(UI.scale(5), l.cmp.sz.y));
	}
	if(sev > 0)
	    l.cmp.add(RichText.render(String.format("Total: $col[128,192,255]{%s} ($col[128,192,255]{%s}/\u2030 hunger)", Utils.odformat2(sev, 2), Utils.odformat2(sev / (1000 * glut), 2)), 0).img,
		      Coord.of(UI.scale(5), l.cmp.sz.y));
	for(int i = 0; i < efs.length; i++) {
	    BufferedImage efi = ItemInfo.longtip(efs[i].info);
	    if(efs[i].p != 1)
		efi = catimgsh(5, efi, RichText.render(String.format("$i{($col[192,192,255]{%d%%} chance)}", (int)Math.round(efs[i].p * 100)), 0).img);
	    l.cmp.add(efi, Coord.of(UI.scale(5), l.cmp.sz.y));
	}
    }
}
