package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeastEatGuardTest {
    @Test
    void permitsOrdinaryInventoryEating() {
        FeastEatGuard guard = new FeastEatGuard(true);
        assertEquals(FeastEatGuard.Decision.ALLOW, guard.beforeEat(FeastEatGuard.TableState.notFeast()));
    }

    @Test
    void blocksUnsafeAndUnloadedTableware() {
        FeastEatGuard guard = new FeastEatGuard(true);
        assertEquals(FeastEatGuard.Decision.BLOCK_UNSAFE,
                guard.beforeEat(FeastEatGuard.TableState.ready("one-left", true)));
        assertEquals(FeastEatGuard.Decision.BLOCK_LOADING,
                guard.beforeEat(FeastEatGuard.TableState.loading()));
    }

    @Test
    void locksSafeEatUntilServerStateChanges() {
        FeastEatGuard guard = new FeastEatGuard(true);
        FeastEatGuard.TableState before = FeastEatGuard.TableState.ready("wear-18", false);
        assertEquals(FeastEatGuard.Decision.ALLOW, guard.beforeEat(before));
        assertEquals(FeastEatGuard.Decision.BLOCK_IN_FLIGHT, guard.beforeEat(before));
        assertEquals(FeastEatGuard.Decision.BLOCK_IN_FLIGHT,
                guard.beforeEat(FeastEatGuard.TableState.loading()));
        assertEquals(FeastEatGuard.Decision.ALLOW,
                guard.beforeEat(FeastEatGuard.TableState.ready("wear-19", false)));
    }

    @Test
    void disabledSettingDoesNotGuardFeastEating() {
        FeastEatGuard guard = new FeastEatGuard(false);
        assertEquals(FeastEatGuard.Decision.ALLOW,
                guard.beforeEat(FeastEatGuard.TableState.loading()));
    }
}
