package nurgling.actions.bots;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static nurgling.actions.bots.MinesweeperSolver.TileState.DANGER;
import static nurgling.actions.bots.MinesweeperSolver.TileState.REVEALED;
import static nurgling.actions.bots.MinesweeperSolver.TileState.SAFE;
import static nurgling.actions.bots.MinesweeperSolver.TileState.WALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinesweeperSolverTest {

    private static final int[][] NEIGHBORS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1}
    };

    @Test
    void numberEqualToUnknownNeighborsMarksThemDanger() {
        MinesweeperSolver solver = new MinesweeperSolver(null);
        solver.putState(0, 0, REVEALED);
        solver.putNumber(0, 0, 1);
        wallNeighborsExcept(solver, 0, 0, new Coord(1, 0));

        solver.solve();

        assertEquals(Set.of(new Coord(1, 0)), dangerSet(solver));
        assertEquals(WALL, solver.getState(new Coord(-1, 0)));
    }

    @Test
    void accountedMinesLeaveRemainingUnknownsSafe() {
        MinesweeperSolver solver = new MinesweeperSolver(null);
        solver.putState(0, 0, REVEALED);
        solver.putNumber(0, 0, 1);
        solver.putState(1, 0, DANGER);
        wallNeighborsExcept(solver, 0, 0, new Coord(1, 0), new Coord(0, 1));

        solver.solve();

        assertEquals(Set.of(new Coord(1, 0)), dangerSet(solver));
        assertEquals(SAFE, solver.getState(new Coord(0, 1)));
    }

    @Test
    void subsetOverlapMarksGuaranteedBomb() {
        MinesweeperSolver solver = new MinesweeperSolver(null);
        solver.putState(0, 0, REVEALED);
        solver.putNumber(0, 0, 1);
        solver.putState(2, 0, REVEALED);
        solver.putNumber(2, 0, 2);
        wallNeighborsExcept(solver, 0, 0, new Coord(1, -1), new Coord(1, 1));
        wallNeighborsExcept(solver, 2, 0, new Coord(1, -1), new Coord(1, 1), new Coord(3, 0));

        solver.solve();

        assertTrue(dangerSet(solver).contains(new Coord(3, 0)));
        assertFalse(dangerSet(solver).contains(new Coord(1, -1)));
        assertFalse(dangerSet(solver).contains(new Coord(1, 1)));
    }

    @Test
    void blankZeroMarksAllUnknownNeighborsSafe() {
        MinesweeperSolver solver = new MinesweeperSolver(null);
        solver.putState(0, 0, REVEALED);
        solver.putNumber(0, 0, 0);

        solver.solve();

        assertTrue(dangerSet(solver).isEmpty());
        Set<Coord> safe = safeSet(solver);
        assertEquals(8, safe.size());
        for (int[] d : NEIGHBORS) {
            assertTrue(safe.contains(new Coord(d[0], d[1])), "safe " + d[0] + "," + d[1]);
        }
    }

    @Test
    void minedFloorWithoutNumberCountsAsZero() {
        MinesweeperSolver solver = new MinesweeperSolver(null);
        solver.putState(0, 0, REVEALED);
        solver.putNumber(0, 0, 1);
        wallNeighborsExcept(solver, 0, 0, new Coord(1, 0));

        solver.promoteAdjacentBlanks();
        solver.solve();

        assertEquals(Set.of(new Coord(1, 0)), dangerSet(solver));
        assertEquals(REVEALED, solver.getState(new Coord(-1, 0)));
        assertEquals(0, solver.getNumber(new Coord(-1, 0)));
    }

    @Test
    void wallNeighborsAreNeverMarkedDanger() {
        MinesweeperSolver solver = new MinesweeperSolver(null);
        solver.putState(0, 0, REVEALED);
        solver.putNumber(0, 0, 1);
        wallNeighborsExcept(solver, 0, 0, new Coord(1, 0));

        solver.solve();

        for (int[] d : NEIGHBORS) {
            Coord n = new Coord(d[0], d[1]);
            if (n.x == 1 && n.y == 0) {
                continue;
            }
            assertEquals(WALL, solver.getState(n), "wall " + n);
            assertFalse(dangerSet(solver).contains(n));
        }
    }

    private static void wallNeighborsExcept(MinesweeperSolver solver, int cx, int cy, Coord... keep) {
        Set<Coord> keepSet = new HashSet<>();
        for (Coord c : keep) {
            keepSet.add(c);
        }
        for (int[] d : NEIGHBORS) {
            Coord n = new Coord(cx + d[0], cy + d[1]);
            if (!keepSet.contains(n)) {
                solver.putState(n.x, n.y, WALL);
            }
        }
    }

    private static Set<Coord> dangerSet(MinesweeperSolver solver) {
        return new HashSet<>(solver.dangerTiles());
    }

    private static Set<Coord> safeSet(MinesweeperSolver solver) {
        return new HashSet<>(solver.safeTiles());
    }
}
