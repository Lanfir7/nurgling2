package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InventoryShapeRoomTest {
    @Test
    void verticalPairAlreadyFits() {
        short[][] grid = filled(6, 6);
        grid[4][4] = 0;
        grid[5][4] = 0;

        InventoryShapeRoom.Plan plan = InventoryShapeRoom.plan(grid, Collections.emptyList(), 1, 2);

        assertEquals(InventoryShapeRoom.Decision.FITS, plan.decision);
        assertNull(plan.move);
    }

    @Test
    void horizontalGapBecomesVerticalByMovingTheCellAbove() {
        short[][] grid = filled(6, 6);
        grid[5][4] = 0;
        grid[5][5] = 0;

        InventoryShapeRoom.Plan plan = InventoryShapeRoom.plan(
                grid,
                Arrays.asList(new InventoryShapeRoom.Piece(4, 4, 1, 1)),
                1, 2);

        assertEquals(InventoryShapeRoom.Decision.SHIFT, plan.decision);
        assertEquals(4, plan.move.fromCol);
        assertEquals(4, plan.move.fromRow);
        assertEquals(5, plan.move.toCol);
        assertEquals(5, plan.move.toRow);
    }

    @Test
    void wideItemUsesTheHorizontalGapAsIs() {
        short[][] grid = filled(6, 6);
        grid[5][4] = 0;
        grid[5][5] = 0;

        InventoryShapeRoom.Plan plan = InventoryShapeRoom.plan(grid, Collections.emptyList(), 2, 1);

        assertEquals(InventoryShapeRoom.Decision.FITS, plan.decision);
    }

    @Test
    void singleSlotIsNotRearranged() {
        short[][] grid = filled(6, 6);
        grid[5][5] = 0;

        InventoryShapeRoom.Plan plan = InventoryShapeRoom.plan(
                grid,
                Arrays.asList(new InventoryShapeRoom.Piece(4, 4, 1, 1)),
                1, 1);

        assertEquals(InventoryShapeRoom.Decision.FITS, plan.decision);
        assertNull(plan.move);
    }

    @Test
    void fullGridHasNoRoomForSingleSlot() {
        InventoryShapeRoom.Plan plan = InventoryShapeRoom.plan(filled(2, 2),
                Collections.emptyList(), 1, 1);
        assertEquals(InventoryShapeRoom.Decision.NO_ROOM, plan.decision);
    }

    @Test
    void skipsWhenNoSingleShiftOpensTheShape() {
        short[][] grid = filled(6, 6);
        grid[5][4] = 0;
        grid[5][5] = 0;

        InventoryShapeRoom.Plan plan = InventoryShapeRoom.plan(
                grid,
                Arrays.asList(new InventoryShapeRoom.Piece(5, 3, 1, 1)),
                1, 2);

        assertEquals(InventoryShapeRoom.Decision.NO_ROOM, plan.decision);
        assertNull(plan.move);
    }

    @Test
    void doesNotMoveAnItemLargerThanOneSlot() {
        short[][] grid = filled(6, 6);
        grid[5][4] = 0;
        grid[5][5] = 0;

        InventoryShapeRoom.Plan plan = InventoryShapeRoom.plan(
                grid,
                Arrays.asList(new InventoryShapeRoom.Piece(4, 4, 1, 2)),
                1, 2);

        assertEquals(InventoryShapeRoom.Decision.NO_ROOM, plan.decision);
    }

    @Test
    void blockedCellIsNotADestination() {
        short[][] grid = filled(2, 2);
        grid[0][1] = 2;
        grid[1][0] = 2;
        grid[1][1] = 0;

        InventoryShapeRoom.Plan plan = InventoryShapeRoom.plan(
                grid,
                Arrays.asList(new InventoryShapeRoom.Piece(0, 0, 1, 1)),
                1, 2);

        assertEquals(InventoryShapeRoom.Decision.NO_ROOM, plan.decision);
    }

    private static short[][] filled(int cols, int rows) {
        short[][] grid = new short[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = 1;
            }
        }
        return grid;
    }
}
