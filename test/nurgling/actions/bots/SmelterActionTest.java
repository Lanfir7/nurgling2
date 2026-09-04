package nurgling.actions.bots;

import haven.Coord2d;
import haven.Gob;
import nurgling.tools.Container;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmelterActionTest {
    @Test
    void fetchesOneAggregateLoadForAllSmelters() throws InterruptedException {
        Container first = container(1, 2);
        Container second = container(2, 3);
        TestOreOps operations = new TestOreOps(0, true);

        boolean exhausted = SmelterAction.fillOreContainers(
                new ArrayList<>(List.of(first, second)), operations);

        assertFalse(exhausted);
        assertEquals(List.of(5), operations.fetchTargets);
        assertEquals(2, operations.transfers);
        assertEquals(0, operations.held);
        assertTrue(first.isFull());
        assertTrue(second.isFull());
    }

    @Test
    void stopsAfterACompleteDepositPassMakesNoProgress() throws InterruptedException {
        Container blocked = container(1, 2);
        TestOreOps operations = new TestOreOps(2, false);

        boolean exhausted = SmelterAction.fillOreContainers(
                new ArrayList<>(List.of(blocked)), operations);

        assertFalse(exhausted);
        assertEquals(1, operations.transfers);
        assertEquals(1, operations.tripsToSmelters);
    }

    @Test
    void reportsExhaustedWhenTheOreAreaProvidesNothing() throws InterruptedException {
        Container empty = container(1, 4);
        TestOreOps operations = new TestOreOps(0, false);

        boolean exhausted = SmelterAction.fillOreContainers(
                new ArrayList<>(List.of(empty)), operations);

        assertTrue(exhausted);
        assertEquals(List.of(4), operations.fetchTargets);
        assertEquals(0, operations.transfers);
    }

    private static Container container(long id, int freeSpace) {
        Container container = new Container(new Gob(null, Coord2d.of(0, 0), id), "smelter", null);
        container.initattr(Container.Space.class);
        container.getattr(Container.Space.class).getRes().put(Container.Space.FREESPACE, freeSpace);
        return container;
    }

    private static class TestOreOps implements SmelterAction.OreOperations {
        int held;
        final boolean allowProgress;
        int transfers;
        int tripsToSmelters;
        final List<Integer> fetchTargets = new ArrayList<>();

        TestOreOps(int held, boolean allowProgress) {
            this.held = held;
            this.allowProgress = allowProgress;
        }

        @Override
        public int held() {
            return held;
        }

        @Override
        public void fetch(int target) {
            fetchTargets.add(target);
            if(allowProgress)
                held = target;
        }

        @Override
        public void goToSmelters() {
            tripsToSmelters++;
        }

        @Override
        public void transfer(Container container) {
            transfers++;
            if(!allowProgress)
                return;
            Container.Space space = container.getattr(Container.Space.class);
            int moved = Math.min(held, space.getFreeSpace());
            held -= moved;
            space.getRes().put(Container.Space.FREESPACE, space.getFreeSpace() - moved);
        }
    }
}
