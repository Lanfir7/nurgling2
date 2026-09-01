package nurgling.widgets.compass;

import haven.Coord;
import haven.Coord2d;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NCompassHitsTest {
    @Test
    void findsOnlyTargetsInsideTheirMarkerRadius() {
        NCompassTarget target = new NCompassTarget("quest", "gob:7",
                NCompassTarget.Kind.QUEST, new Coord2d(1, 2), "Quest", 3,
                null, Color.WHITE, 7);
        NCompassHits hits = new NCompassHits();
        hits.add(new Coord(50, 20), 12, target);

        assertSame(target, hits.find(new Coord(58, 25)));
        assertNull(hits.find(new Coord(70, 20)));

        hits.clear();
        assertNull(hits.find(new Coord(50, 20)));
    }
}
