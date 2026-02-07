package nurgling.gattrr;

import haven.*;
import haven.render.*;
import nurgling.tools.MaterialFactory;
import nurgling.tools.VSpec;
import nurgling.NStyle;

import java.awt.Color;

/**
 * Gob attribute that applies color tint to largechest based on fill status.
 * Green = empty, Red = full, Yellow = partially filled
 */
public class NLargeChestColor extends GAttrib implements Gob.SetupMod {
    
    private static final Color COLOR_FREE = new Color(0, 100, 0, 128);      // Darker green tint
    private static final Color COLOR_FULL = new Color(120, 0, 0, 128);      // Darker red tint
    private static final Color COLOR_NOTFREE = new Color(140, 140, 0, 110); // Darker yellow tint
    
    private Pipe.Op fx;
    private MaterialFactory.Status lastStatus = null;
    
    public NLargeChestColor(Gob g) {
        super(g);
        // Defer first color update to gobstate() to avoid issues during initialization
        this.fx = new MixColor(COLOR_NOTFREE); // Default color
    }
    
    private void updateColor() {
        try {
            if (gob == null || gob.ngob == null) {
                return;
            }
            long marker = gob.ngob.getModelAttribute();
            MaterialFactory.Status status = getStatus((int) marker);
            
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
            // Ignore errors during color update to avoid breaking rendering
        }
    }
    
    private MaterialFactory.Status getStatus(int mask) {
        int freeMask = VSpec.largechest_state.get(NStyle.Container.FREE);
        int fullMask = VSpec.largechest_state.get(NStyle.Container.FULL);

        if ((mask & ~freeMask) == 0) {
            return MaterialFactory.Status.FREE;
        } else if ((mask & fullMask) == fullMask) {
            return MaterialFactory.Status.FULL;
        } else {
            return MaterialFactory.Status.NOTFREE;
        }
    }
    
    @Override
    public Pipe.Op gobstate() {
        // Update color on each frame in case marker changed
        try {
            updateColor();
        } catch (Exception e) {
            // Ignore to avoid breaking rendering
        }
        return fx;
    }
}
