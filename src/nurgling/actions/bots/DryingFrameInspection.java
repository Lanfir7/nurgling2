package nurgling.actions.bots;

import haven.Gob;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.MaterialFactory;

import java.util.ArrayList;

/** Empty drying frames are tinted green. That tint is enough to skip opening them just to see what hangs there. */
public final class DryingFrameInspection {
    private DryingFrameInspection() {
    }

    public static boolean greenMask(int mask) {
        return MaterialFactory.getStatus("gfx/terobjs/dframe", mask) == MaterialFactory.Status.FREE;
    }

    public static boolean visuallyEmpty(Gob gob) {
        if (gob == null || gob.ngob == null || !"gfx/terobjs/dframe".equals(gob.ngob.name))
            return false;
        int mask = gob.ngob.customMask ? gob.ngob.mask() : (int) gob.ngob.getModelAttribute();
        return greenMask(mask);
    }

    public static ArrayList<Container> withoutEmpty(ArrayList<Container> containers) {
        ArrayList<Container> kept = new ArrayList<>();
        for (Container container : containers) {
            if (!visuallyEmpty(Finder.findGob(container.gobHash)))
                kept.add(container);
        }
        return kept;
    }
}
