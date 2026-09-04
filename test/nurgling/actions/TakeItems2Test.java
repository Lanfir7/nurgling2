package nurgling.actions;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import nurgling.NInventory;
import nurgling.tools.Container;
import nurgling.widgets.Specialisation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakeItems2Test {

    @Test
    void pileWithdrawalSettlesAfterApproachBeforeOpening() throws InterruptedException {
        StringBuilder order = new StringBuilder();

        Results result = TakeItems2.approachSettleAndOpenPile(
                () -> {
                    order.append('A');
                    return Results.SUCCESS();
                },
                () -> order.append('S'),
                () -> {
                    order.append('O');
                    return Results.SUCCESS();
                });

        assertTrue(result.IsSuccess());
        assertEquals("ASO", order.toString());
    }

    @Test
    void pileWithdrawalIsClampedToInventoryCapacity() {
        assertEquals(12, TakeItems2.pileTransferCount(50, 12));
        assertEquals(0, TakeItems2.pileTransferCount(50, 0));
    }

    @Test
    void fullCellGridCanStillAcceptAnItemIntoItsExistingStack() {
        assertFalse(TakeItems2.inventoryCannotAcceptItem(0, true));
    }

    @Test
    void noFreeCellAndNoExistingStackMeansInventoryIsFull() {
        assertTrue(TakeItems2.inventoryCannotAcceptItem(0, false));
    }

    @Test
    void observedItemShapeRetargetsTheLoadToRealDestinationCapacity() {
        Container tetrisTarget = container(1);
        tetrisTarget.initattr(Container.Tetris.class);
        Container.Tetris tetris = tetrisTarget.getattr(Container.Tetris.class);
        tetris.getRes().put(Container.Tetris.SRC, new short[3][2]);
        tetris.getRes().put(Container.Tetris.TARGET_COORD,
                new ArrayList<>(List.of(new Coord(2, 1), new Coord(1, 1))));

        Container regularTarget = container(2);
        regularTarget.initattr(Container.Space.class);
        regularTarget.getattr(Container.Space.class).getRes().put(Container.Space.FREESPACE, 3);

        assertEquals(5, TakeItems2.capacityForShape(
                new ArrayList<>(List.of(tetrisTarget, regularTarget)), new Coord(2, 1)));
    }

    @Test
    void aliasGatheringCannotAccidentallyEnterTheSingleNameFlow() throws InterruptedException {
        TakeItems2 action = new TakeItems2(
                null, 4, Specialisation.SpecName.ore, NInventory.QualityType.High);

        assertFalse(action.run(null).IsSuccess());
    }

    private static Container container(long id) {
        return new Container(new Gob(null, Coord2d.of(0, 0), id), "test", null);
    }
}
