package nurgling.overlays;

import haven.Coord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

    private static final Set<String> ORE_BASE = oreBase();

    private static Set<String> oreBase() {
        Set<String> ores = new HashSet<String>();
        String[] names = {
                "argentite", "blackcoal", "blackore", "bloodstone", "cassiterite",
                "chalcopyrite", "cinnabar", "cuprite", "direvein", "galena",
                "heavyearth", "hematite", "hornsilver", "ilmenite", "ironochre",
                "leadglance", "leafore", "limonite", "magnetite", "malachite",
                "meteorite", "nagyagite", "peacockore", "petzite", "schrifterz",
                "silvershine", "sylvanite", "wineglance"
        };
        for (int i = 0; i < names.length; i++) {
            ores.add(names[i]);
        }
        return ores;
    }

    private static final class Unmatched {
        final Set<Coord> walls;
        double age;

        Unmatched(Set<Coord> walls) {
            this.walls = walls == null || walls.isEmpty()
                    ? Collections.<Coord>emptySet()
                    : new HashSet<Coord>(walls);
        }

        boolean matches(Coord tile) {
            return walls.isEmpty() || walls.contains(tile);
        }
    }

    private final ArrayDeque<Pending> pending = new ArrayDeque<Pending>();
    private final List<Sample> samples = new ArrayList<Sample>();
    private final ArrayDeque<Unmatched> unmatched = new ArrayDeque<Unmatched>();

    public static boolean isOreRock(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) {
            return false;
        }
        int slash = resourceName.lastIndexOf('/');
        String base = slash >= 0 ? resourceName.substring(slash + 1) : resourceName;
        String n = base.toLowerCase().replace("-", "").replace("_", "").replace(" ", "");
        return ORE_BASE.contains(n);
    }

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
        Unmatched hit = takeUnmatched(tile);
        if (hit != null) {
            samples.add(new Sample(tile, true));
            return;
        }
        pending.addLast(new Pending(tile));
    }

    public void ignoreTile(Coord tile) {
        if (tile == null) {
            return;
        }
        Iterator<Pending> pit = pending.iterator();
        while (pit.hasNext()) {
            if (tile.equals(pit.next().tile)) {
                pit.remove();
            }
        }
        Iterator<Sample> sit = samples.iterator();
        while (sit.hasNext()) {
            if (tile.equals(sit.next().center)) {
                sit.remove();
            }
        }
    }

    public void onGlimmer() {
        onGlimmer(null);
    }

    public void onGlimmer(Set<Coord> expectedWalls) {
        Pending p = pending.pollFirst();
        if (p != null) {
            samples.add(new Sample(p.tile, true));
            return;
        }
        unmatched.addLast(new Unmatched(expectedWalls));
    }

    private Unmatched takeUnmatched(Coord tile) {
        Iterator<Unmatched> it = unmatched.iterator();
        while (it.hasNext()) {
            Unmatched u = it.next();
            if (u.matches(tile)) {
                it.remove();
                return u;
            }
        }
        return null;
    }

    public void tick(double dt) {
        if (!unmatched.isEmpty()) {
            ArrayList<Unmatched> expired = new ArrayList<Unmatched>();
            for (Unmatched u : unmatched) {
                u.age += dt;
                if (u.age >= UNMATCHED_HOLD) {
                    expired.add(u);
                }
            }
            unmatched.removeAll(expired);
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
        unmatched.clear();
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
