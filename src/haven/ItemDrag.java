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

import nurgling.NWItem;
import nurgling.tools.*;

public class ItemDrag extends NWItem
{
    public Coord doff;
    
    public ItemDrag(Coord dc, GItem item) {
	super(item);
	z(100);
	this.doff = dc;
    }

    protected void added() {
	this.c = parent.ui.mc.add(doff.inv());
	ui.grabmouse(this);
    }
    
    public void drawmain(GOut g, GSprite spr) {
	g.chcolor(255, 255, 255, 128);
	super.drawmain(g, spr);
	g.chcolor();
    }

    public boolean mousedown(MouseDownEvent ev) {
	if(!ev.grabbed)
	    return(false);
	 nurgling.hotkeys.HotkeyResolver resolver = new nurgling.hotkeys.HotkeyResolver(nurgling.hotkeys.Hotkeys.registry());
	 nurgling.hotkeys.HotkeyAction action = resolver.firstMouse(nurgling.hotkeys.HotkeyContext.HELD_ITEM, ev.b, ui.modflags());
	if(action != null && nurgling.hotkeys.Hotkeys.HELD_DROP_ON_TARGET.equals(action.id()) &&
		ui.dispatchq(parent, new Drop(ev.c.add(this.c), this)).handled)
		return(true);
	if(action != null && nurgling.hotkeys.Hotkeys.HELD_OPEN_WITHOUT_USING.equals(action.id())) {
		monitoring.StockpileStorageTracker.rememberHand(this);
	    GameUI gui = getparent(GameUI.class);
	    if((gui != null) && (gui.map != null)) {
		return gui.map.heldItemRmb(gui.map.rootxlate(ev.c.add(rootpos())),
			action.canonicalMods() == null ? 0 : action.canonicalMods());
		}
	}
	if(action != null && (nurgling.hotkeys.Hotkeys.HELD_LIGHT_FROM_FIRE.equals(action.id()) ||
		nurgling.hotkeys.Hotkeys.HELD_INTERACT_WITH_TARGET.equals(action.id())) &&
		ui.dispatchq(parent, new Interact(ev.c.add(this.c), this,
			action.canonicalMods() == null ? 0 : action.canonicalMods())).handled)
		return(true);
	if(action == null && ev.b == 3 && ui.modctrl && !ui.modshift && !ui.modmeta) {
	    /* XXX */
	    GameUI gui = getparent(GameUI.class);
	    if((gui != null) && (gui.map != null)) {
		return gui.map.heldItemRmb(gui.map.rootxlate(ev.c.add(rootpos())), 0);
	    }
	}

	return(false);
    }

    public void mousemove(MouseMoveEvent ev) {
	this.c = this.c.add(ev.c.sub(doff));
    }
}
