package haven.sprites;

import haven.Gob;
import haven.OptWnd;
import haven.render.RenderTree;

import java.awt.*;


public class AggroCircleSprite extends ColoredCircleSprite {
    public static Color COMBAT_FOE_COLOR = OptWnd.combatFoeColorOptionWidget.currentColor;
    private boolean alive = true;

    public AggroCircleSprite(final Gob g) {
        super(g, COMBAT_FOE_COLOR, 4.6f, 6.1f, 0.5f);
    }

    public void rem() {
        alive = false;
    }

    @Override
    public boolean tick(double ddt) {
        return !alive;
    }

    @Override
    public void added(RenderTree.Slot slot) {
        super.added(slot);
    }
}
