package nurgling.overlays;

import haven.Coord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class GlimmerHeatmap {
    public static final int RADIUS = 3;
    public static final double GLIMMER_WAIT = 0.8;
    /** Glimmer often arrives while the wall is still rock; keep it until that tile falls. */
    public static final double UNMATCHED_HOLD = 20.0;

    private static final class Pending {
        final Coord tile;
        double wait;

        Pending(Coord tile) {
            this.tile = tile;
        }
    }

    private static final class Sample {
        final Coord center;
        final boolean positive;

        Sample(Coord center, boolean positive) {
            this.center = center;
            this.positive = positive;
        }
    }

    private final ArrayDeque<Pending> pending = new ArrayDeque<Pending>();
    private final List<Sample> samples = new ArrayList<Sample>();
    private int unmatched;
    private double unmatchedAge;

    public static boolean inRange(Coord center, Coord tile) {
        if (center == null || tile == null) {
            return false;
        }
        int dx = Math.abs(center.x - tile.x);
        int dy = Math.abs(center.y - tile.y);
        return Math.max(dx, dy) <= RADIUS;
    }

    public void onTileCompleted(Coord tile) {
        if (tile == null) {
            return;
        }
        if (unmatched > 0) {
            unmatched--;
            samples.add(new Sample(tile, true));
            return;
        }
        pending.addLast(new Pending(tile));
    }

    public void onGlimmer() {
        Pending p = pending.pollFirst();
        if (p != null) {
            samples.add(new Sample(p.tile, true));
            return;
        }
        unmatched++;
        unmatchedAge = 0;
    }

    public void tick(double dt) {
        if (unmatched > 0) {
            unmatchedAge += dt;
            if (unmatchedAge >= UNMATCHED_HOLD) {
                unmatched = 0;
                unmatchedAge = 0;
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        ArrayList<Pending> timedOut = new ArrayList<Pending>();
        for (Pending p : pending) {
            p.wait += dt;
            if (p.wait >= GLIMMER_WAIT) {
                timedOut.add(p);
            }
        }
        for (Pending p : timedOut) {
            pending.remove(p);
            samples.add(new Sample(p.tile, false));
        }
    }

    public void clear() {
        pending.clear();
        samples.clear();
        unmatched = 0;
        unmatchedAge = 0;
    }

    public Map<Coord, Integer> visibleHeat(Coord playerTile, int drawRadius,
                                           Function<Coord, Boolean> stillRock) {
        if (playerTile == null || stillRock == null) {
            return Collections.emptyMap();
        }
        Set<Coord> excluded = new HashSet<Coord>();
        Map<Coord, Integer> heat = new HashMap<Coord, Integer>();
        for (Sample sample : samples) {
            for (int x = sample.center.x - RADIUS; x <= sample.center.x + RADIUS; x++) {
                for (int y = sample.center.y - RADIUS; y <= sample.center.y + RADIUS; y++) {
                    Coord tile = Coord.of(x, y);
                    if (Math.max(Math.abs(tile.x - playerTile.x), Math.abs(tile.y - playerTile.y)) > drawRadius) {
                        continue;
                    }
                    Boolean rock = stillRock.apply(tile);
                    if (rock == null || !rock.booleanValue()) {
                        continue;
                    }
                    if (!sample.positive) {
                        excluded.add(tile);
                        heat.remove(tile);
                    } else if (!excluded.contains(tile)) {
                        Integer prev = heat.get(tile);
                        heat.put(tile, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
                    }
                }
            }
        }
        return heat;
    }
}
