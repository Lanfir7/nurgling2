package nurgling.actions.bots;

import haven.Gob;
import haven.Coord2d;
import haven.MCache;
import nurgling.NGameUI;
import nurgling.NHitBox;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.PathFinder;
import nurgling.actions.Results;
import nurgling.tasks.GateDetector;
import nurgling.tasks.NTask;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;

import java.util.Map;

/** Opens or closes the nearest gate per the step's configured mode; a no-op if it's already in that state. */
public class GateBot implements Action {

    private static final double DETECT_RADIUS = MCache.tilesz.x * 3;
    private static final double INTERACT_RADIUS = MCache.tilesz.x * 1.2;
    private static final long STATE_POLL_TIMEOUT_MS = 5000;
    private static final double STAND_OFF = MCache.tilesz.x / 2.0;

    private final boolean wantOpen;

    public GateBot() {
        this.wantOpen = true;
    }

    public GateBot(Map<String, Object> settings) {
        Object v = settings != null ? settings.get("mode") : null;
        this.wantOpen = !"close".equals(v);
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null)
            return Results.ERROR("Player not found.");

        Gob gate = Finder.findGob(player.rc, new NAlias(GateDetector.GATE_NAMES), null, DETECT_RADIUS);
        if (gate == null)
            return Results.ERROR("GateBot: no gate found nearby.");

        if (GateDetector.isDoorOpen(gate) == wantOpen) {
            return Results.SUCCESS();
        }

        Results pathResult;
        if (!wantOpen) {
            pathResult = new PathFinder(frontOf(gate, player.rc)).run(gui);
        } else if (player.rc.dist(gate.rc) > INTERACT_RADIUS) {
            pathResult = new PathFinder(gate).run(gui);
        } else {
            pathResult = Results.SUCCESS();
        }
        if (!pathResult.IsSuccess()) {
            return Results.ERROR("GateBot: couldn't reach the " + (wantOpen ? "gate" : "front side of the gate") + ".");
        }
        NUtils.rclickGob(gate);
        boolean changed = waitForGateState(gate, wantOpen, STATE_POLL_TIMEOUT_MS);
        if (!changed)
            return Results.ERROR("GateBot: gate didn't " + (wantOpen ? "open" : "close") + " in time.");

        return Results.SUCCESS();
    }

    private static Coord2d frontOf(Gob gate, Coord2d from) {
        NHitBox hitBox = gate.ngob != null ? gate.ngob.hitBox : null;
        boolean thinX = hitBox == null || (hitBox.end.x - hitBox.begin.x) <= (hitBox.end.y - hitBox.begin.y);
        double halfThin = hitBox == null ? MCache.tilesz.x / 2.0
                : (thinX ? hitBox.end.x - hitBox.begin.x : hitBox.end.y - hitBox.begin.y) / 2.0;
        return frontOf(gate.rc, gate.a, thinX, halfThin, from);
    }

    /** Pure geometry seam: a closing character must remain on its current side of the gate. */
    static Coord2d frontOf(Coord2d gateCenter, double gateAngle, boolean thinX, double halfThin, Coord2d from) {
        Coord2d axis = (thinX ? new Coord2d(1, 0) : new Coord2d(0, 1)).rot(gateAngle);
        double side = Math.signum((from.x - gateCenter.x) * axis.x + (from.y - gateCenter.y) * axis.y);
        if (side == 0) side = 1;
        return gateCenter.add(axis.mul(side * (halfThin + STAND_OFF)));
    }

    private boolean waitForGateState(Gob gate, boolean wantOpen, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        NUtils.addTask(new NTask() {
            @Override
            public boolean check() {
                return System.currentTimeMillis() > deadline || (gate.ngob != null && GateDetector.isDoorOpen(gate) == wantOpen);
            }
        });
        return gate.ngob != null && GateDetector.isDoorOpen(gate) == wantOpen;
    }
}
