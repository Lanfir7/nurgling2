package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinWorklistTest {

    private static final String ORE = "gfx/tiles/rocks/cassiterite";
    private static final String OTHER = "gfx/tiles/rocks/gneiss";
    private static final Coord SEED = new Coord(5, 5);

    @Test
    void wallFaceEnqueuesOnlyVisibleNeighbours() {
        VeinWorklist list = new VeinWorklist(ORE, SEED);
        Map<Coord, String> visible = new HashMap<Coord, String>();
        visible.put(new Coord(5, 4), ORE);
        visible.put(new Coord(4, 5), ORE);
        visible.put(new Coord(6, 5), ORE);
        Set<Coord> safe = new HashSet<Coord>(visible.keySet());
        list.scanVisible(typeFn(visible), safeFn(safe));

        Set<Coord> got = drain(list, SEED);
        assertEquals(Set.of(new Coord(5, 4), new Coord(4, 5), new Coord(6, 5)), got);
    }

    @Test
    void hiddenDiagonalJoinsOnlyAfterConnectingTileIsMined() {
        VeinWorklist list = new VeinWorklist(ORE, SEED);
        Map<Coord, String> visible = new HashMap<Coord, String>();
        visible.put(new Coord(5, 4), ORE);
        Set<Coord> safe = new HashSet<Coord>();
        safe.add(new Coord(5, 4));
        safe.add(new Coord(4, 4));
        list.scanVisible(typeFn(visible), safeFn(safe));
        assertEquals(new Coord(5, 4), list.takeNearest(SEED));
        list.markMined(new Coord(5, 4));

        visible.put(new Coord(4, 4), ORE);
        list.scanVisible(typeFn(visible), safeFn(safe));
        assertEquals(new Coord(4, 4), list.takeNearest(SEED));
    }

    @Test
    void takeNearestPicksClosestToPlayer() {
        VeinWorklist list = new VeinWorklist(ORE, SEED);
        list.offer(new Coord(6, 5), ORE, true);
        list.offer(new Coord(5, 4), ORE, true);
        assertEquals(new Coord(5, 4), list.takeNearest(new Coord(0, 5)));
    }

    @Test
    void unsafeAndOtherTypeAreRejected() {
        VeinWorklist list = new VeinWorklist(ORE, SEED);
        list.offer(new Coord(5, 4), ORE, false);
        list.offer(new Coord(6, 5), OTHER, true);
        list.offer(new Coord(4, 5), null, true);
        assertTrue(list.isEmpty());
        assertNull(list.takeNearest(SEED));
    }

    private static Function<Coord, String> typeFn(Map<Coord, String> visible) {
        return visible::get;
    }

    private static Function<Coord, Boolean> safeFn(Set<Coord> safe) {
        return safe::contains;
    }

    private static Set<Coord> drain(VeinWorklist list, Coord player) {
        Set<Coord> got = new HashSet<Coord>();
        Coord next;
        while ((next = list.takeNearest(player)) != null) {
            got.add(next);
            list.markMined(next);
        }
        return got;
    }
}
