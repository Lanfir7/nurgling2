package nurgling.tasks;

import haven.Coord2d;
import nurgling.tools.Finder;

public class WaitConstructionObject extends NTask {
    Coord2d position;

    public WaitConstructionObject(Coord2d position) {
        this.position = position;
    }

    public static WaitConstructionObject withSoftTimeout(Coord2d position, int ticks) {
        WaitConstructionObject wait = new WaitConstructionObject(position);
        wait.infinite = false;
        wait.maxCounter = ticks;
        wait.criticalOnTimeout = false;
        return wait;
    }

    @Override
    public boolean check() {
        return Finder.findGob(position) != null;
    }
}
