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

public interface DTarget {
    public default boolean drop(Coord cc, Coord ul) {return(false);}
    public default boolean drop(Coord cc, Coord ul, int mods) {return(drop(cc, ul));}
    public default boolean iteminteract(Coord cc, Coord ul) {return(false);}
    public default boolean iteminteract(Coord cc, Coord ul, int mods) {
	return(iteminteract(cc, ul));
    }

    public default boolean drop(Drop ev) {
	return(drop(ev.c, ev.src == null ? Coord.z : ev.c.sub(ev.src.doff), ev.mods));
    }
    public default boolean iteminteract(Interact ev) {
	return(iteminteract(ev.c, ev.src == null ? Coord.z : ev.c.sub(ev.src.doff), ev.mods));
    }

    public abstract static class ItemEvent extends Widget.MouseEvent {
	public final ItemDrag src;
	public final ItemEvent root;
	public boolean handled;

	public ItemEvent(Coord c, ItemDrag src) {
	    super(c);
	    this.src = src;
	    this.root = this;
	}
	public ItemEvent(ItemEvent from, Coord c) {
	    super(from, c);
	    this.src = from.src;
	    this.root = from.root;
	}
    }

    public static class Drop extends ItemEvent {
	public final int mods;
	public Drop(Coord c, ItemDrag src) {this(c, src, 0);}
	public Drop(Coord c, ItemDrag src, int mods) {super(c, src); this.mods = mods;}
	public Drop(Drop from, Coord c) {super(from, c); this.mods = from.mods;}
	public Drop derive(Coord c) {return(new Drop(this, c));}

	protected boolean shandle(Widget w) {
	    if((w != src) && (w instanceof DTarget) && ((DTarget)w).drop(this)) {
		root.handled = true;
		return(true);
	    }
	    return(super.shandle(w));
	}
    }

    public static class Interact extends ItemEvent {
	public final int mods;
	public Interact(Coord c, ItemDrag src) {this(c, src, src == null || src.ui == null ? 0 : src.ui.modflags());}
	public Interact(Coord c, ItemDrag src, int mods) {super(c, src); this.mods = mods;}
	public Interact(Interact from, Coord c) {super(from, c); this.mods = from.mods;}
	public Interact derive(Coord c) {return(new Interact(this, c));}

	protected boolean shandle(Widget w) {
	    if((w != src) && (w instanceof DTarget) && ((DTarget)w).iteminteract(this)) {
		root.handled = true;
		return(true);
	    }
	    return(super.shandle(w));
	}
    }
}
