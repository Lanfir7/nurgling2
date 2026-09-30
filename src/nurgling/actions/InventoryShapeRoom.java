package nurgling.actions;

import haven.Coord;
import haven.Inventory;
import haven.UI;
import haven.WItem;
import haven.Widget;
import nurgling.NInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a multi-slot item can be placed, or whether one 1x1 item
 * inside the container should move so the free cells form the needed rectangle.
 */
final class InventoryShapeRoom {
    enum Decision { FITS, SHIFT, NO_ROOM }

    static final class Piece {
        final int col;
        final int row;
        final int width;
        final int height;

        Piece(int col, int row, int width, int height) {
            this.col = col;
            this.row = row;
            this.width = width;
            this.height = height;
        }
    }

    static final class Move {
        final int fromCol;
        final int fromRow;
        final int toCol;
        final int toRow;

        Move(int fromCol, int fromRow, int toCol, int toRow) {
            this.fromCol = fromCol;
            this.fromRow = fromRow;
            this.toCol = toCol;
            this.toRow = toRow;
        }
    }

    static final class Plan {
        final Decision decision;
        final Move move;

        private Plan(Decision decision, Move move) {
            this.decision = decision;
            this.move = move;
        }
    }

    private InventoryShapeRoom() {}

    /** Build the plan from the same live grid and item coordinates used by the chest UI. */
    static Plan planFor(NInventory inventory, WItem incoming) {
        if (inventory == null || incoming == null || incoming.item == null || incoming.item.spr == null)
            return new Plan(Decision.NO_ROOM, null);
        short[][] grid = inventory.containerMatrix();
        if (grid == null) return new Plan(Decision.NO_ROOM, null);
        Coord size = incoming.item.spr.sz().div(UI.scale(32));
        List<Piece> pieces = new ArrayList<>();
        for (Widget child = inventory.child; child != null; child = child.next) {
            if (!(child instanceof WItem)) continue;
            WItem item = (WItem) child;
            if (item.item.spr == null) return new Plan(Decision.NO_ROOM, null);
            Coord itemSize = item.item.spr.sz().div(UI.scale(32));
            Coord position = item.c.div(Inventory.sqsz);
            pieces.add(new Piece(position.x, position.y, itemSize.x, itemSize.y));
        }
        return plan(grid, pieces, size.x, size.y);
    }

    static Plan plan(short[][] grid, List<Piece> pieces, int width, int height) {
        if (width < 1 || height < 1) return new Plan(Decision.NO_ROOM, null);
        if (fits(grid, width, height))
            return new Plan(Decision.FITS, null);
        if (width * height <= 1) return new Plan(Decision.NO_ROOM, null);
        if (pieces != null) {
            for (Piece piece : pieces) {
                if (piece == null || piece.width != 1 || piece.height != 1)
                    continue;
                if (!occupied(grid, piece.col, piece.row))
                    continue;
                int rows = grid.length;
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < grid[row].length; col++) {
                        if (grid[row][col] != 0)
                            continue;
                        short[][] next = copy(grid);
                        next[piece.row][piece.col] = 0;
                        next[row][col] = 1;
                        if (fits(next, width, height))
                            return new Plan(Decision.SHIFT, new Move(piece.col, piece.row, col, row));
                    }
                }
            }
        }
        return new Plan(Decision.NO_ROOM, null);
    }

    static boolean fits(short[][] grid, int width, int height) {
        if (grid == null || width < 1 || height < 1 || grid.length < height)
            return false;
        int rows = grid.length;
        for (int row = 0; row <= rows - height; row++) {
            int cols = grid[row].length;
            if (cols < width)
                continue;
            for (int col = 0; col <= cols - width; col++) {
                if (rectFree(grid, col, row, width, height))
                    return true;
            }
        }
        return false;
    }

    private static boolean rectFree(short[][] grid, int col, int row, int width, int height) {
        for (int y = row; y < row + height; y++) {
            if (y >= grid.length || grid[y].length < col + width)
                return false;
            for (int x = col; x < col + width; x++) {
                if (grid[y][x] != 0)
                    return false;
            }
        }
        return true;
    }

    private static boolean occupied(short[][] grid, int col, int row) {
        return grid != null && row >= 0 && row < grid.length
                && col >= 0 && col < grid[row].length
                && grid[row][col] != 0 && grid[row][col] != 2;
    }

    private static short[][] copy(short[][] grid) {
        short[][] next = new short[grid.length][];
        for (int i = 0; i < grid.length; i++)
            next[i] = grid[i].clone();
        return next;
    }
}
