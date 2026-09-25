package nurgling.cheese;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConveyorOrdersManagerTest {
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
