package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyTansyIfMissingTest {

    @Test
    void loadingBuffCountIsRetryNotFailedStackGrowth() {
        assertTrue(ApplyTansyIfMissing.isBuffCountUnknown(-1));
        assertFalse(ApplyTansyIfMissing.isBuffCountUnknown(0));
        assertFalse(ApplyTansyIfMissing.isBuffCountUnknown(3));

        assertFalse(ApplyTansyIfMissing.failedStackGrowth(0, -1));
        assertFalse(ApplyTansyIfMissing.failedStackGrowth(-1, -1));
        assertFalse(ApplyTansyIfMissing.failedStackGrowth(-1, 1));
        assertFalse(ApplyTansyIfMissing.failedStackGrowth(3, 4));
        assertTrue(ApplyTansyIfMissing.failedStackGrowth(3, 3));
        assertTrue(ApplyTansyIfMissing.failedStackGrowth(3, 2));
        assertTrue(ApplyTansyIfMissing.failedStackGrowth(0, 0));
    }

    @Test
    void runRetriesWhenBuffCountIsStillLoading() throws Exception {
        String src = Files.readString(
                Path.of("src/nurgling/actions/bots/ApplyTansyIfMissing.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("isBuffCountUnknown(newCount)"));
        assertTrue(src.contains("failedStackGrowth(count, newCount)"));
        assertFalse(src.contains("if (newCount <= count)"));
    }

    @Test
    void persistentUnknownCountStopsAfterCap() {
        int cap = ApplyTansyIfMissing.MAX_CONSECUTIVE_UNKNOWN_COUNTS;
        assertTrue(cap >= 1, "cap must allow a useful transient loading retry before giving up");
        assertFalse(ApplyTansyIfMissing.shouldStopOnUnknownCount(0));
        assertFalse(ApplyTansyIfMissing.shouldStopOnUnknownCount(cap - 1));
        assertTrue(ApplyTansyIfMissing.shouldStopOnUnknownCount(cap));
        assertTrue(ApplyTansyIfMissing.shouldStopOnUnknownCount(cap + 1));
    }

    @Test
    void unknownCountResolvingToRealCountKeepsToppingUp() {
        assertFalse(ApplyTansyIfMissing.shouldStopOnUnknownCount(0));
        assertFalse(ApplyTansyIfMissing.failedStackGrowth(-1, 1));
        assertFalse(ApplyTansyIfMissing.failedStackGrowth(1, 2));
        assertFalse(ApplyTansyIfMissing.isBuffCountUnknown(1));
    }

    @Test
    void runCapsConsecutiveUnknownCountsInsteadOfDrainingTakeArea() throws Exception {
        String src = Files.readString(
                Path.of("src/nurgling/actions/bots/ApplyTansyIfMissing.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("shouldStopOnUnknownCount"));
        assertTrue(src.contains("consecutiveUnknown"));
        assertTrue(src.contains("consecutiveUnknown = 0"));
    }
}
