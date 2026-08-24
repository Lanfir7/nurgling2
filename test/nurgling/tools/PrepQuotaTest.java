package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrepQuotaTest {
    @Test
    void parseEmptyAndJunkIsUnlimited() {
        assertEquals(0, PrepQuota.parse(""));
        assertEquals(0, PrepQuota.parse("0"));
        assertEquals(0, PrepQuota.parse("  "));
        assertEquals(0, PrepQuota.parse("x"));
        assertEquals(0, PrepQuota.parse("-1"));
        assertEquals(0, PrepQuota.parse(null));
    }

    @Test
    void parsePositive() {
        assertEquals(50, PrepQuota.parse("50"));
        assertEquals(7, PrepQuota.parse(" 7 "));
    }

    @Test
    void unlimitedNeverReached() {
        assertFalse(PrepQuota.reached(0, 10, 0));
        assertFalse(PrepQuota.reached(-3, 100, 100));
    }

    @Test
    void reachedCountsInventoryPlusPiled() {
        assertFalse(PrepQuota.reached(50, 10, 0));
        assertTrue(PrepQuota.reached(50, 10, 40));
        assertTrue(PrepQuota.reached(50, 0, 50));
        assertFalse(PrepQuota.reached(50, 10, 10));
    }

    @Test
    void isLogOnlyTreeLogs() {
        assertTrue(PrepQuota.isLog("gfx/terobjs/trees/oaklog"));
        assertFalse(PrepQuota.isLog("gfx/terobjs/trees/oak"));
        assertFalse(PrepQuota.isLog("gfx/terobjs/arch/logcabin"));
        assertFalse(PrepQuota.isLog(null));
    }

    @Test
    void spaceBeatsDrink() {
        assertEquals(PrepQuota.Halt.NOFREESPACE,
                PrepQuota.pickBoards(false, false, true, true));
        assertEquals(PrepQuota.Halt.TIMEFORDRINK,
                PrepQuota.pickBoards(false, false, false, true));
        assertEquals(PrepQuota.Halt.LOGNOTFOUND,
                PrepQuota.pickBoards(true, true, true, true));
        assertEquals(PrepQuota.Halt.DANGER,
                PrepQuota.pickBoards(false, true, true, true));
    }

    @Test
    void woundOverwritesBlocksHalt() {
        assertEquals(PrepQuota.Halt.WOUND_DANGER,
                PrepQuota.pickBlocks(false, false, true, true, true));
        assertEquals(PrepQuota.Halt.NOFREESPACE,
                PrepQuota.pickBlocks(false, false, true, true, false));
    }
}
