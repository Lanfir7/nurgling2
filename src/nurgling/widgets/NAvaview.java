package nurgling.widgets;

import haven.*;
import nurgling.widgets.login.NLoginTheme;

public class NAvaview extends Avaview {
    private static final int SPINMIN = UI.scale(80);

    public NAvaview(Coord sz, long avagob, String camnm) {
        super(sz, avagob, camnm);
    }

    @Override
    public void draw(GOut g) {
        if (avagob == -1) {
            try {
                updcomp();
            } catch (Loading l) {
                if ((sz.x >= SPINMIN) && (sz.y >= SPINMIN))
                    NLoginTheme.drawSpinner(g, sz.div(2), UI.scale(26), UI.scale(7));
                return;
            }
        }
        super.draw(g);
    }
}
