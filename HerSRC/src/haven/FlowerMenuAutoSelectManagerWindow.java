package haven;

import haven.res.ui.pag.toggle.Toggle;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class FlowerMenuAutoSelectManagerWindow extends Window {
    private PetalList petalList;
    public static CheckBox flowerMenuAutoSelectCheckBox;

    public FlowerMenuAutoSelectManagerWindow() {
        super(UI.scale(300, 350), "Auto-Select Manager (Flower Menus)");

        add(flowerMenuAutoSelectCheckBox = new CheckBox("Enable Flower-Menu Auto-Select") {
            {
                a = Utils.getprefb("flowerMenuAutoSelect", false);
            }
            public void set(boolean val) {
                a = val;
                GameUI.flowerMenuAutoSelect = val;
                Utils.setprefb("flowerMenuAutoSelect", val);
                if (ui != null && ui.gui != null) {
                    ui.gui.optionInfoMsg("Flower-Menu Auto-Select is now " + (val ? "ENABLED" : "DISABLED") + "!", (val ? OptWnd.msgGreen : OptWnd.msgRed), Audio.resclip(val ? Toggle.sfxon : Toggle.sfxoff));
                }
            }
        }, UI.scale(10, 10));

//        add(new Button(UI.scale(100), "Refresh List"){
//            @Override
//            public void click() {
//                refresh();
//            }
//        }, UI.scale(210, 2));

        add(new Label("Auto-Select Options:", new Text.Foundry(Text.sans, 12)), UI.scale(106, 40));

        petalList = new PetalList(UI.scale(296), 12);
        add(petalList, UI.scale(25, 60));
        refresh();
        add(new Label("New options are added to this list as you discover them.", new Text.Foundry(Text.sans, 12)), UI.scale(5, 335));
        add(new Label("The list is automatically refreshed when a new item is added."), UI.scale(12, 355));
        this.c = new Coord (200, 100);
        pack();
    }

    public void refresh() {
        petalList.clearItems();
        for(Map.Entry<String, Boolean> petal: FlowerMenu.autoSelectMap.entrySet()){
            petalList.addItem(new PetalItem(petal.getKey(), petal.getValue()));
        }
    }


    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if((sender == this) && (Objects.equals(msg, "close"))) {
            hide();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    public class PetalList extends Widget {

        ArrayList<PetalItem> items = new ArrayList<>();
        Scrollbar sb;
        int rowHeight = UI.scale(22);
        int rows, w;

        public PetalList(int w, int rows) {
            this.rows = rows;
            this.w = w;
            this.sz = new Coord(w, rowHeight * rows);
            sb = new Scrollbar(rowHeight * rows, 0, 100);
            add(sb, UI.scale(0, 0));
        }

        public PetalItem listitem(int i) {
            return items.get(i);
        }

        public void addItem(PetalItem item) {
            add(item);
            items.add(item);
        }

        public void clearItems() {
            this.children().forEach(w -> {if(w instanceof PetalItem)w.destroy();});
            items.clear();
        }

        public int listitems() {
            return items.size();
        }

        @Override
        public boolean mousewheel(MouseWheelEvent ev) {
            sb.ch(ev.a);
            return true;
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            int row = ev.c.y / rowHeight + sb.val;
            if(row >= items.size())
                return super.mousedown(ev);
            if(items.get(row).mousedown(new MouseDownEvent(ev.c.sub(UI.scale(15), ev.c.y / rowHeight * rowHeight), ev.b)))
                return true;
            return super.mousedown(ev);
        }

        @Override
        public boolean mouseup(MouseUpEvent ev) {
            int row = ev.c.y / rowHeight + sb.val;
            if(row >= items.size())
                return super.mouseup(ev);
            if(items.get(row).mouseup(new MouseUpEvent(ev.c.sub(UI.scale(15), ev.c.y / rowHeight * rowHeight), ev.b)))
                return true;
            return super.mouseup(ev);
        }

        @Override
        public void draw(GOut g) {
            sb.max = items.size()-rows;
            for(int i=0; i<rows; i++) {
                if(i+sb.val >= items.size())
                    break;
                GOut ig = g.reclip(new Coord(UI.scale(15), i*rowHeight), new Coord(w-UI.scale(15), rowHeight));
                items.get(i+sb.val).draw(ig);
            }
            super.draw(g);
        }

    }

    public static class PetalItem extends Widget {

        public String name;

        public PetalItem(String name, boolean value) {
            this.name = name;
            add(new CheckBox(name) {
                {
                    a = value;
                }
                public void set(boolean val) {
                    FlowerMenu.updateValue(name, val);
                    a = val;
                }
            }, UI.scale(0, 5));
        }

        @Override
        public void draw(GOut g) {
            super.draw(g);
        }

        @Override
        public void mousemove(MouseMoveEvent ev) {
            if(ev.c.x > 470)
                super.mousemove(new MouseMoveEvent(ev.c.sub(UI.scale(15), 0)));
            else
                super.mousemove(ev);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            // ND: This is the old mousedown method basically.
            // Child widgets of widgets can't be clicked anymore for some reason but idk how to fix it for now, so just use this old one.
            Coord c = ev.c;
            int button = ev.b;
            for(Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
                if(!wdg.visible())
                    continue;
                Coord cc = xlate(wdg.c, true);
                if(c.isect(cc, wdg.sz)) {
                    if(wdg.mousedown(new MouseDownEvent(c.add(cc.inv()), button))) {
                        return(true);
                    }
                }
            }
            return(false);
        }
    }
}
