package haven;

import static haven.Equipory.etts;
import static haven.Inventory.sqsz;

public class QuickSlotsWdg extends Widget implements DTarget {
    private static final Tex sbg = Resource.loadtex("gfx/hud/quickslots");
    public Coord leftHandSlotCoord, rightHandSlotCoord, leftPouchSlotCoord, rightPouchSlotCoord, beltSlotCoord, backpackSlotCoord, capeSlotCoord = null;
    public static final int slotSpacing = UI.scale(2);

    public static final Tex slotSquareBg = Inventory.invsq;
    public static final Tex leftHandBg = Resource.loadtex("gfx/hud/equip/ep6");
    public static final Tex rightHandBg = Resource.loadtex("gfx/hud/equip/ep7");
    public static final Tex leftPouchBg = Resource.loadtex("gfx/hud/equip/ep19");
    public static final Tex rightPouchBg = Resource.loadtex("gfx/hud/equip/ep20");
    public static final Tex beltBg = Resource.loadtex("gfx/hud/equip/ep5");
    public static final Tex backpackBg = Resource.loadtex("gfx/hud/equip/ep11");
    public static final Tex capeBg = Resource.loadtex("gfx/hud/equip/ep14");
    public int shownSlots = 0;

    private UI.Grab dragging;
    private Coord dc;

    public QuickSlotsWdg() {
        super(UI.scale(new Coord(0, 0)));
        reloadSlots();
    }

    @Override
    public void draw(GOut g) {
        if (!GameUI.showUI)
            return;
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            if (shownSlots > 0) {
                Coord slotLocation = new Coord(0, 0);
                for (int i = 0; i < shownSlots; i++) {
                    g.image(slotSquareBg, slotLocation);
                    slotLocation.x += slotSquareBg.sz().x + slotSpacing;
                }
            }

            if (leftHandSlotCoord != null){
                g.image(leftHandBg, leftHandSlotCoord);
                if (e.slots[6] != null)
                    e.slots[6].draw(g.reclipl(leftHandSlotCoord, slotSquareBg.sz()));
            }
            if (rightHandSlotCoord != null){
                g.image(rightHandBg, rightHandSlotCoord);
                if (e.slots[7] != null)
                    e.slots[7].draw(g.reclipl(rightHandSlotCoord, slotSquareBg.sz()));
            }

            if (leftPouchSlotCoord != null) {
                g.image(leftPouchBg, leftPouchSlotCoord);
                if (e.slots[19] != null)
                    e.slots[19].draw(g.reclipl(leftPouchSlotCoord, slotSquareBg.sz()));
            }

            if (rightPouchSlotCoord != null) {
                g.image(rightPouchBg, rightPouchSlotCoord);
                if (e.slots[20] != null)
                    e.slots[20].draw(g.reclipl(rightPouchSlotCoord, slotSquareBg.sz()));
            }

            if (beltSlotCoord != null) {
                g.image(beltBg, beltSlotCoord);
                if (e.slots[5] != null)
                    e.slots[5].draw(g.reclipl(beltSlotCoord, slotSquareBg.sz()));
            }

            if (backpackSlotCoord != null) {
                g.image(backpackBg, backpackSlotCoord);
                if (e.slots[11] != null)
                    e.slots[11].draw(g.reclipl(backpackSlotCoord, slotSquareBg.sz()));
            }

            if (capeSlotCoord != null) {
                g.image(capeBg, capeSlotCoord);
                if (e.slots[14] != null)
                    e.slots[14].draw(g.reclipl(capeSlotCoord, slotSquareBg.sz()));
            }

        }
    }


    @Override
    public boolean drop(Coord cc, Coord ul) {
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            if (leftHandSlotCoord != null && cc.x > leftHandSlotCoord.x && cc.x <= leftHandSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 6);
            if (rightHandSlotCoord != null && cc.x > rightHandSlotCoord.x && cc.x <= rightHandSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 7);
            if (leftPouchSlotCoord != null && cc.x > leftPouchSlotCoord.x && cc.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 19);
            if (rightPouchSlotCoord != null && cc.x > rightPouchSlotCoord.x && cc.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 20);
            if (beltSlotCoord != null && cc.x > beltSlotCoord.x && cc.x <= beltSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 5);
            if (backpackSlotCoord != null && cc.x > backpackSlotCoord.x && cc.x <= backpackSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 11);
            if (capeSlotCoord != null && cc.x > capeSlotCoord.x && cc.x <= capeSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 14);
            return true;
        }
        return false;
    }

    @Override
    public boolean iteminteract(Coord cc, Coord ul) {
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            WItem w = null;
            if (leftHandSlotCoord != null && cc.x > leftHandSlotCoord.x && cc.x <= leftHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[6];
            if (rightHandSlotCoord != null && cc.x > rightHandSlotCoord.x && cc.x <= rightHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[7];
            if (leftPouchSlotCoord != null && cc.x > leftPouchSlotCoord.x && cc.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[19];
            if (rightPouchSlotCoord != null && cc.x > rightPouchSlotCoord.x && cc.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[20];
            if (beltSlotCoord != null && cc.x > beltSlotCoord.x && cc.x <= beltSlotCoord.x + slotSquareBg.sz().x) w = e.slots[5];
            if (backpackSlotCoord != null && cc.x > backpackSlotCoord.x && cc.x <= backpackSlotCoord.x + slotSquareBg.sz().x) w = e.slots[11];
            if (capeSlotCoord != null && cc.x > capeSlotCoord.x && cc.x <= capeSlotCoord.x + slotSquareBg.sz().x) w = e.slots[14];
            if (w != null) {
                return w.iteminteract(cc, ul);
            }
        }
        return false;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Equipory e = ui.gui.getequipory();
        if(e != null) {
            WItem w = null;
            if (leftHandSlotCoord != null && c.x > leftHandSlotCoord.x && c.x <= leftHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[6];
            if (rightHandSlotCoord != null && c.x > rightHandSlotCoord.x && c.x <= rightHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[7];
            if (leftPouchSlotCoord != null && c.x > leftPouchSlotCoord.x && c.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[19];
            if (rightPouchSlotCoord != null && c.x > rightPouchSlotCoord.x && c.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[20];
            if (beltSlotCoord != null && c.x > beltSlotCoord.x && c.x <= beltSlotCoord.x + slotSquareBg.sz().x) w = e.slots[5];
            if (backpackSlotCoord != null && c.x > backpackSlotCoord.x && c.x <= backpackSlotCoord.x + slotSquareBg.sz().x) w = e.slots[11];
            if (capeSlotCoord != null && c.x > capeSlotCoord.x && c.x <= capeSlotCoord.x + slotSquareBg.sz().x) w = e.slots[14];
            if(w != null) {
                return w.tooltip(c, (prev == this) ? w : prev);
            } else {
                return null;
            }
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousehover(MouseHoverEvent ev, boolean on) {
        Coord c = ev.c;
        Equipory e = ui.gui.getequipory();
        if(e != null) {
            WItem w = null;
            Coord hoverCoord = null;
            if (leftHandSlotCoord != null && c.x > leftHandSlotCoord.x && c.x <= leftHandSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(leftHandSlotCoord.x + slotSquareBg.sz().x, leftHandSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[6];
            }
            if (rightHandSlotCoord != null && c.x > rightHandSlotCoord.x && c.x <= rightHandSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(rightHandSlotCoord.x + slotSquareBg.sz().x, rightHandSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[7];
            }
            if (leftPouchSlotCoord != null && c.x > leftPouchSlotCoord.x && c.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(leftPouchSlotCoord.x + slotSquareBg.sz().x, leftPouchSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[19];
            }
            if (rightPouchSlotCoord != null && c.x > rightPouchSlotCoord.x && c.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(rightPouchSlotCoord.x + slotSquareBg.sz().x, rightPouchSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[20];
            }
            if (beltSlotCoord != null && c.x > beltSlotCoord.x && c.x <= beltSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(beltSlotCoord.x + slotSquareBg.sz().x, beltSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[5];
            }
            if (backpackSlotCoord != null && c.x > backpackSlotCoord.x && c.x <= backpackSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(backpackSlotCoord.x + slotSquareBg.sz().x, backpackSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[11];
            }
            if (capeSlotCoord != null && c.x > capeSlotCoord.x && c.x <= capeSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(capeSlotCoord.x + slotSquareBg.sz().x, capeSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[14];
            }
            if(w != null) {
                if(on && (w.item.contents != null && (!OptWnd.showHoverInventoriesWhenHoldingShiftCheckBox.a || ui.modshift))) {
                    Widget hover = new Widget();
                    QuickSlotsWdg.this.add(hover, hoverCoord);
                    w.item.hovering(hover);
                    return(true);
                }
                return w.mousehover(ev, on);
            }
        }
        return(false);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if (!GameUI.showUI)
            return false;
        if (ui.modmeta || ui.modctrl)
            return true;
        if (ev.b == 2) {
            if((dragging != null)) { // ND: I need to do this extra check and remove it in case you do another click before the mouseup. Idk why it has to be done like this, but it solves the issue.
                dragging.remove();
                dragging = null;
            }
            dragging = ui.grabmouse(this);
            dc = ev.c;
            return true;
        }
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            WItem w = null;
            if (leftHandSlotCoord != null && ev.c.x > leftHandSlotCoord.x && ev.c.x <= leftHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[6];
            if (rightHandSlotCoord != null && ev.c.x > rightHandSlotCoord.x && ev.c.x <= rightHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[7];
            if (leftPouchSlotCoord != null && ev.c.x > leftPouchSlotCoord.x && ev.c.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[19];
            if (rightPouchSlotCoord != null && ev.c.x > rightPouchSlotCoord.x && ev.c.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[20];
            if (beltSlotCoord != null && ev.c.x > beltSlotCoord.x && ev.c.x <= beltSlotCoord.x + slotSquareBg.sz().x) w = e.slots[5];
            if (backpackSlotCoord != null && ev.c.x > backpackSlotCoord.x && ev.c.x <= backpackSlotCoord.x + slotSquareBg.sz().x) w = e.slots[11];
            if (capeSlotCoord != null && ev.c.x > capeSlotCoord.x && ev.c.x <= capeSlotCoord.x + slotSquareBg.sz().x) w = e.slots[14];
            if (w != null) {
                w.mousedown(new MouseDownEvent(new Coord(w.sz.x / 2, w.sz.y / 2), ev.b));
                return true;
            } else if (ev.b == 1) {
                if((dragging != null)) { // ND: I need to do this extra check and remove it in case you do another click before the mouseup. Idk why it has to be done like this, but it solves the issue.
                    dragging.remove();
                    dragging = null;
                }
                dragging = ui.grabmouse(this);
                dc = ev.c;
                return true;
            }
        }
        return (super.mousedown(ev));
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        checkIfOutsideOfUI(); // ND: Prevent the widget from being dragged outside the current window size
        if((dragging != null)) {
            dragging.remove();
            dragging = null;
            Utils.setprefc("wndc-quickslots", this.c);
            return true;
        }
        return super.mouseup(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        if (dragging != null) {
            this.c = this.c.add(ev.c.x, ev.c.y).sub(dc);
            return;
        }
        super.mousemove(ev);
    }

    public void checkIfOutsideOfUI() {
        if (this.c.x < 0)
            this.c.x = 0;
        if (this.c.y < 0)
            this.c.y = 0;
        if (this.c.x > (ui.gui.sz.x - this.sz.x))
            this.c.x = ui.gui.sz.x - this.sz.x;
        if (this.c.y > (ui.gui.sz.y - this.sz.y))
            this.c.y = ui.gui.sz.y - this.sz.y;
    }

    public void reloadSlots(){
        shownSlots = 0;
        leftHandSlotCoord = setSlotCoord(OptWnd.leftHandQuickSlotCheckBox.a);
        rightHandSlotCoord = setSlotCoord(OptWnd.rightHandQuickSlotCheckBox.a);
        leftPouchSlotCoord = setSlotCoord(OptWnd.leftPouchQuickSlotCheckBox.a);
        rightPouchSlotCoord = setSlotCoord(OptWnd.rightPouchQuickSlotCheckBox.a);
        beltSlotCoord = setSlotCoord(OptWnd.beltQuickSlotCheckBox.a);
        backpackSlotCoord = setSlotCoord(OptWnd.backpackQuickSlotCheckBox.a);
        capeSlotCoord = setSlotCoord(OptWnd.capeQuickSlotCheckBox.a);
        if (shownSlots > 0) {
            this.sz = new Coord((slotSquareBg.sz().x * shownSlots) + (slotSpacing * (shownSlots-1)), slotSquareBg.sz().y);
        } else {
            this.sz = new Coord(0, 0);
        }

    }

    private Coord setSlotCoord(boolean isEnabled) {
        if (isEnabled) {
            Coord coord = new Coord((slotSquareBg.sz().x + slotSpacing) * shownSlots, 0);
            shownSlots++;
            return coord;
        } else {
            return null;
        }
    }

}