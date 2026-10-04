package nurgling.widgets;

import haven.CheckBox;
import haven.Coord;
import haven.GOut;
import haven.UI;
import haven.Widget;

import java.util.function.Consumer;

/** Small checkbox popup opened by right-click on a minimap overlay button. */
public class HomeOverlayMenu extends Widget {
    private UI.Grab mouseGrab;
    private UI.Grab keyGrab;

    public HomeOverlayMenu(String label, boolean checked, Consumer<Boolean> onChange) {
        super(Coord.z);
        z(100);
        CheckBox box = new CheckBox(label) {
            @Override
            public void changed(boolean val) {
                onChange.accept(val);
            }
        };
        box.a = checked;
        int pad = UI.scale(6);
        add(box, new Coord(pad, pad));
        pack();
        resize(sz.add(pad, pad));
    }

    @Override
    protected void added() {
        mouseGrab = ui.grabmouse(this);
        keyGrab = ui.grabkeys(this);
    }

    @Override
    public void destroy() {
        if (mouseGrab != null)
            mouseGrab.remove();
        if (keyGrab != null)
            keyGrab.remove();
        super.destroy();
    }

    @Override
    public void draw(GOut g) {
        g.chcolor(24, 20, 16, 210);
        g.frect(Coord.z, sz);
        g.chcolor(196, 153, 83, 220);
        g.rect(Coord.z, sz.sub(1, 1));
        g.chcolor();
        super.draw(g);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if (!ev.propagate(this))
            close();
        return true;
    }

    @Override
    public boolean keydown(KeyDownEvent ev) {
        if (key_esc.match(ev)) {
            close();
            return true;
        }
        return false;
    }

    private void close() {
        ui.destroy(this);
    }
}
