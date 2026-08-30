package nurgling.tasks;

import haven.Coord2d;
import haven.Gob;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitPileTest {
    @Test
    void softTimeoutStopsWaitingWithoutKillingTheBot() {
        WaitPile wait = WaitPile.withSoftTimeout(Coord2d.of(10, 20), 80);

        assertFalse(wait.infinite);
        assertFalse(wait.criticalOnTimeout);
        assertEquals(80, wait.maxCounter);
    }

    @Test
    void nonStockpileAtTheTargetPositionIsNotReportedAsCreatedPile() {
        WaitPile wait = new WaitPile(Coord2d.of(10, 20));
        Gob rock = new Gob(null, Coord2d.of(10, 20), 1);
        rock.ngob.name = "gfx/terobjs/bumlings/stone";

        assertFalse(wait.acceptCandidate(rock));
        assertNull(wait.getPile());
    }

    @Test
    void matchingStockpileIsReportedOnlyAfterItsNamePasses() {
        WaitPile wait = new WaitPile(Coord2d.of(10, 20));
        Gob stockpile = new Gob(null, Coord2d.of(10, 20), 2);
        stockpile.ngob.name = "gfx/terobjs/stockpile-soil";

        assertTrue(wait.acceptCandidate(stockpile));
        assertSame(stockpile, wait.getPile());
    }

    @Test
    void matchingStockpileIsFoundWhenAnotherGobOccupiesTheSamePosition() {
        WaitPile wait = new WaitPile(Coord2d.of(10, 20));
        Gob item = new Gob(null, Coord2d.of(10, 20), 3);
        item.ngob.name = "gfx/invobjs/stone";
        Gob stockpile = new Gob(null, Coord2d.of(10, 20), 4);
        stockpile.ngob.name = "gfx/terobjs/stockpile-stone";

        assertTrue(wait.acceptCandidates(Arrays.asList(item, stockpile)));
        assertSame(stockpile, wait.getPile());
    }
}
