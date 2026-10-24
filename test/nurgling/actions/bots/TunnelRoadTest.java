package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Button;
import nurgling.actions.ExtendRoad;
import nurgling.NGob;
import nurgling.widgets.TunnelingDialog.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TunnelRoadTest {
    @Test
    void delayedFirstMilestoneWindowPreventsTryingAnotherCandidate() throws Exception {
        Gob first = NGob.getDummy(Coord2d.of(-110, 0), 0, (String) null);
        Gob second = NGob.getDummy(Coord2d.of(-99, 0), 0, (String) null);
        AtomicInteger calls = new AtomicInteger();
        TunnelRoad.StartChoice timedOut = TunnelRoad.chooseStartingPost(List.of(first, second), gob -> {
            calls.incrementAndGet();
            return new ExtendRoad.OpenExtend(null, null, true);
        });
        assertTrue(timedOut.pending);
        assertEquals(1, calls.get());

        calls.set(0);
        TunnelRoad.StartChoice resolved = TunnelRoad.chooseStartingPost(List.of(first, second), gob -> {
            return calls.incrementAndGet() == 1 ? new ExtendRoad.OpenExtend(null, null, false)
                    : new ExtendRoad.OpenExtend(new Button(70, "Extend"), null, false);
        });
        assertFalse(resolved.pending);
        assertSame(second, resolved.gob);
        assertEquals(2, calls.get());
    }
    @Test
    void spacingAndDueDecisionPreserveRoadRange() {
        assertEquals(5, TunnelRoad.clampSpacing(-3));
        assertEquals(30, TunnelRoad.clampSpacing(90));
        Coord2d tail = Coord2d.of(-110, -55);
        assertFalse(TunnelRoad.isDue(tail, Coord2d.of(-99, -55), Coord2d.of(-88, -55), 55));
        assertTrue(TunnelRoad.isDue(tail, Coord2d.of(-66, -55), Coord2d.of(-44, -55), 55));
        assertFalse(TunnelRoad.isDue(tail, Coord2d.of(-66, -55), Coord2d.of(-55, -55), 55));
    }

    @Test
    void catchUpStepMovesWithinRangeForEveryCardinalAndNegativeTiles() {
        Coord2d tail = TunnelRoad.tileCenter(Coord.of(-40, -20));
        for (Direction direction : Direction.values()) {
            Coord target = Coord.of(-40 + 70 * direction.dx, -20 + 70 * direction.dy);
            Coord step = TunnelRoad.stepToward(tail, TunnelRoad.tileCenter(target), 30 * 11);
            double oldDist = tail.dist(TunnelRoad.tileCenter(target));
            assertTrue(TunnelRoad.tileCenter(step).dist(tail) < 30 * 11);
            assertTrue(TunnelRoad.tileCenter(step).dist(TunnelRoad.tileCenter(target)) < oldDist);
            assertTrue(step.x < 0 || step.y < 0, "negative world coordinates stay signed");
        }
        assertEquals(0, TunnelRoad.headingOf(Direction.EAST), 0.001);
        assertEquals(Math.PI / 2, TunnelRoad.headingOf(Direction.SOUTH), 0.001);
        assertEquals(Math.PI, TunnelRoad.headingOf(Direction.WEST), 0.001);
        assertEquals(3 * Math.PI / 2, TunnelRoad.headingOf(Direction.NORTH), 0.001);
    }

    @Test
    void replacementTailMustBeTheSameMilestoneAtTheSameSite() {
        Gob old = NGob.getDummy(Coord2d.of(-104.5, 38.5), 0, (String) null);
        old.ngob.name = "gfx/terobjs/road/milestone-stone";
        assertTrue(TunnelRoad.isSameMilestone(old, Coord2d.of(-104.5, 38.5)));
        assertFalse(TunnelRoad.isSameMilestone(old, Coord2d.of(-88, 38.5)));
        old.ngob.name = "gfx/terobjs/consobj";
        assertFalse(TunnelRoad.isSameMilestone(old, old.rc));
    }
}
