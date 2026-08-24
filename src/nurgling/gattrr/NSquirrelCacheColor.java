package nurgling.gattrr;

import haven.*;
import haven.render.*;
import nurgling.tools.MaterialFactory;

import java.awt.Color;

/**
 * Gob attribute that tints squirrel cache: green if empty (marker 2), red at state 6.
 */
public class NSquirrelCacheColor extends GAttrib implements Gob.SetupMod {

    static final int MARKER_EMPTY = 2;
    static final int MARKER_FULL = 6;

    private static final Color COLOR_FREE = new Color(0, 100, 0, 128);
    private static final Color COLOR_FULL = new Color(120, 0, 0, 128);
    private static final Color COLOR_NOTFREE = new Color(140, 140, 0, 110);

    private Pipe.Op fx;
    private MaterialFactory.Status lastStatus = null;

    public NSquirrelCacheColor(Gob g) {
        super(g);
        this.fx = new MixColor(COLOR_NOTFREE);
    }

    static MaterialFactory.Status statusOf(int marker) {
        if (marker == MARKER_FULL) {
            return MaterialFactory.Status.FULL;
        }
        if (marker == MARKER_EMPTY) {
            return MaterialFactory.Status.FREE;
        }
        return MaterialFactory.Status.NOTFREE;
    }

    private void updateColor() {
        try {
            if (gob == null || gob.ngob == null) {
                return;
            }
            MaterialFactory.Status status = statusOf((int) gob.ngob.getModelAttribute());
            if (status != lastStatus) {
                lastStatus = status;
                Color color;
                switch (status) {
                    case FREE:
                        color = COLOR_FREE;
                        break;
                    case FULL:
                        color = COLOR_FULL;
                        break;
                    case NOTFREE:
                    default:
                        color = COLOR_NOTFREE;
                        break;
                }
                this.fx = new MixColor(color);
            }
        } catch (Exception e) {
        }
    }

    @Override
    public Pipe.Op gobstate() {
        try {
            updateColor();
        } catch (Exception e) {
        }
        return fx;
    }
}
