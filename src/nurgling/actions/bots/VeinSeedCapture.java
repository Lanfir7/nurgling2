package nurgling.actions.bots;

import haven.Coord;

public class VeinSeedCapture {
    private Coord seed;
    private boolean armed;

    public synchronized void arm() {
        seed = null;
        armed = true;
    }

    public synchronized void disarm() {
        armed = false;
    }

    public synchronized boolean offer(Coord a, Coord b) {
        if (!armed || a == null) {
            return false;
        }
        seed = a;
        armed = false;
        return true;
    }

    public synchronized Coord peek() {
        return seed;
    }

    public synchronized boolean isArmed() {
        return armed;
    }
}
