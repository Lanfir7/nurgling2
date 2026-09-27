package nurgling.cheese;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConveyorOrdersManagerTest {
    @Test
    void stageBatchesKeepOldestArrivalAcrossMovementAndSave() {
        ConveyorOrder order = ConveyorOrder.create(1, "Caciotta");
        CheeseBranch.Place start = CheeseBranch.Place.start;
        CheeseBranch.Place cellar = CheeseBranch.Place.cellar;
        CheeseBranch.Place inside = CheeseBranch.Place.inside;

        order.addTrays(2);
        order.advance("Sheep's Curd", start, 2, 1_000L);
        order.addTrays(2);
        order.advance("Sheep's Curd", start, 2, 2_000L);
        assertEquals(1_000L, order.oldestArrival("Feta", cellar));

        order.advance("Feta", cellar, 3, 3_000L);
        assertEquals(1, order.findStep("Feta", cellar).left);
        assertEquals(2_000L, order.oldestArrival("Feta", cellar));
        assertEquals(3_000L, order.oldestArrival("Caciotta", inside));

        ConveyorOrder loaded = new ConveyorOrder(order.toJson());
        assertEquals(2_000L, loaded.oldestArrival("Feta", cellar));
        assertEquals(1, loaded.stageBatches("Feta", cellar).get(0).count);
        assertEquals(3_000L, loaded.oldestArrival("Caciotta", inside));
    }

    @Test
    void olderOrderWithoutBatchesShowsUnknownArrival() {
        ConveyorOrder order = ConveyorOrder.create(1, "Caciotta");
        CheeseBranch.Place cellar = CheeseBranch.Place.cellar;
        order.getStatus().add(new CheeseOrder.StepStatus("Feta", cellar.name(), 3));
        JSONObject oldJson = order.toJson();
        oldJson.remove("batches");

        ConveyorOrder loaded = new ConveyorOrder(oldJson);
        assertEquals(0L, loaded.oldestArrival("Feta", cellar));
        assertEquals(3, loaded.stageBatches("Feta", cellar).get(0).count);
        assertEquals(0L, new ConveyorOrder(loaded.toJson()).oldestArrival("Feta", cellar));
    }

    @Test
    void olderUnstampedTraysLeaveBeforeNewlyStampedTrays() {
        ConveyorOrder order = ConveyorOrder.create(1, "Caciotta");
        CheeseBranch.Place cellar = CheeseBranch.Place.cellar;
        order.getStatus().add(new CheeseOrder.StepStatus("Feta", cellar.name(), 3));

        order.addTrays(2);
        order.advance("Sheep's Curd", CheeseBranch.Place.start, 2, 2_000L);
        order.advance("Feta", cellar, 3, 3_000L);

        assertEquals(2, order.findStep("Feta", cellar).left);
        assertEquals(2_000L, order.oldestArrival("Feta", cellar));
    }

    @Test
    void intermediateCheeseIsNotSlicedWhenBookedForLaterStage() {
        ConveyorOrder jorbonzola = ConveyorOrder.create(1, "Jorbonzola");
        ConveyorOrder midnightBlue = ConveyorOrder.create(2, "Midnight Blue Cheese");
        jorbonzola.makeContinuous(1, 1);
        midnightBlue.makeContinuous(1, 1);

        CheeseBranch.Place mine = CheeseBranch.Place.mine;
        midnightBlue.getStatus().add(new CheeseOrder.StepStatus("Jorbonzola", "mine", 1));
        assertNull(ConveyorOrdersManager.sliceOrder(Arrays.asList(jorbonzola, midnightBlue), "Jorbonzola", mine));
        assertSame(midnightBlue, ConveyorOrdersManager.orderFor(Arrays.asList(jorbonzola, midnightBlue), "Jorbonzola", mine));

        jorbonzola.getStatus().add(new CheeseOrder.StepStatus("Jorbonzola", "mine", 1));
        assertSame(jorbonzola, ConveyorOrdersManager.sliceOrder(Arrays.asList(jorbonzola, midnightBlue), "Jorbonzola", mine));
        jorbonzola.sliced();
        assertEquals(0, jorbonzola.findStep("Jorbonzola", mine).left);
        assertNull(ConveyorOrdersManager.sliceOrder(Arrays.asList(jorbonzola, midnightBlue), "Jorbonzola", mine));
    }

    @Test
    void sharedIntermediateMovementSplitsBookedWorkBeforeContinuousOverflow() {
        ConveyorOrder caciotta = ConveyorOrder.create(1, "Caciotta");
        ConveyorOrder cabrales = ConveyorOrder.create(2, "Cabrales");
        caciotta.makeContinuous(1, 1);
        cabrales.makeContinuous(1, 1);

        CheeseBranch.Place cellar = CheeseBranch.Place.cellar;
        caciotta.getStatus().add(new CheeseOrder.StepStatus("Feta", cellar.name(), 2));
        cabrales.getStatus().add(new CheeseOrder.StepStatus("Feta", cellar.name(), 3));

        Map<CheeseBranch.Place, Integer> destinations = ConveyorOrdersManager.destinationsFor(
                Arrays.asList(caciotta, cabrales), "Feta", cellar, 9);
        assertEquals(6, destinations.get(CheeseBranch.Place.inside));
        assertEquals(3, destinations.get(CheeseBranch.Place.outside));

        assertTrue(ConveyorOrdersManager.advanceMoved(Arrays.asList(caciotta, cabrales), "Feta", cellar,
                CheeseBranch.Place.inside, 6));
        assertEquals(0, caciotta.findStep("Feta", cellar).left);
        assertEquals(6, caciotta.findStep("Caciotta", CheeseBranch.Place.inside).left);
        assertEquals(3, cabrales.findStep("Feta", cellar).left);

        assertTrue(ConveyorOrdersManager.advanceMoved(Arrays.asList(caciotta, cabrales), "Feta", cellar,
                CheeseBranch.Place.outside, 3));
        assertEquals(0, cabrales.findStep("Feta", cellar).left);
        assertEquals(3, cabrales.findStep("Cabrales", CheeseBranch.Place.outside).left);
    }
}
