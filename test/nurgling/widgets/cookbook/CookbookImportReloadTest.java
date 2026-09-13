package nurgling.widgets.cookbook;

import nurgling.actions.Results;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookbookImportReloadTest {
    @Test
    void successRequestsReload() {
        assertTrue(CookbookImportReload.shouldReload(Results.SUCCESS()));
    }

    @Test
    void failDoesNotReload() {
        assertFalse(CookbookImportReload.shouldReload(Results.FAIL()));
    }

    @Test
    void nullDoesNotReload() {
        assertFalse(CookbookImportReload.shouldReload(null));
    }

    @Test
    void startOnlyWhenPendingReadyAndIdle() {
        assertTrue(CookbookImportReload.shouldStartReload(true, true, false));
        assertFalse(CookbookImportReload.shouldKeepPending(true, true, false));
    }

    @Test
    void keepPendingWhileDbNotReady() {
        assertFalse(CookbookImportReload.shouldStartReload(true, false, false));
        assertTrue(CookbookImportReload.shouldKeepPending(true, false, false));
        assertFalse(CookbookImportReload.shouldStartReload(true, false, true));
        assertTrue(CookbookImportReload.shouldKeepPending(true, false, true));
    }

    @Test
    void keepPendingWhileLoaderBusy() {
        assertFalse(CookbookImportReload.shouldStartReload(true, true, true));
        assertTrue(CookbookImportReload.shouldKeepPending(true, true, true));
    }

    @Test
    void noPendingNeverStartsOrKeeps() {
        assertFalse(CookbookImportReload.shouldStartReload(false, true, false));
        assertFalse(CookbookImportReload.shouldKeepPending(false, true, false));
        assertFalse(CookbookImportReload.shouldStartReload(false, false, true));
        assertFalse(CookbookImportReload.shouldKeepPending(false, false, true));
    }

    @Test
    void failAndNullDoNotArmPendingCycle() {
        assertFalse(CookbookImportReload.shouldReload(Results.FAIL()));
        assertFalse(CookbookImportReload.shouldReload(null));
        assertFalse(CookbookImportReload.shouldStartReload(false, true, false));
        assertFalse(CookbookImportReload.shouldKeepPending(false, true, false));
    }
}
