package nurgling.tools;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthHomeAutoSaverTest {
    @Test
    void recognizesDestinationOnlyAfterTeleportAndPositionChange() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        Coord origin = Coord.of(10, 10);

        saver.onAction(origin, "travel", "hearth");
        assertFalse(saver.hasReachedDestination(Coord.of(20, 20)));

        saver.onTeleportStarted();
        assertFalse(saver.hasReachedDestination(origin));
        assertTrue(saver.hasReachedDestination(Coord.of(20, 20)));
    }

    @Test
    void savesAfterConfirmedTeleportWhenOwnerMessageIsNotRepeated() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        AtomicInteger saves = new AtomicInteger();

        saver.onAction("travel", "hearth");
        saver.onTeleportStarted();
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });

        assertEquals(1, saves.get());
        assertFalse(saver.isArmed());
    }

    @Test
    void recognizesOnlyTravelToOwnHearth() {
        assertTrue(HearthHomeAutoSaver.isHearthTravel("travel", "hearth"));
        assertFalse(HearthHomeAutoSaver.isHearthTravel("travel", "village"));
        assertFalse(HearthHomeAutoSaver.isHearthTravel("hearth"));
        assertFalse(HearthHomeAutoSaver.isHearthTravel());
    }

    @Test
    void savesOnlyAfterDestinationTerritoryHasSettled() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        AtomicInteger saves = new AtomicInteger();

        saver.onAction("travel", "hearth");
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS * 2, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });
        assertEquals(0, saves.get());

        saver.onTeleportStarted();
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS / 2, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });
        assertEquals(0, saves.get());

        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });
        assertEquals(1, saves.get());

        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS * 2, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });
        assertEquals(1, saves.get());
    }

    @Test
    void rejectedTravelCannotCaptureAClaimEnteredNormally() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        AtomicInteger saves = new AtomicInteger();

        saver.onAction("travel", "hearth");
        saver.onTerritoryUpdated(true);
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS * 2, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });

        assertEquals(0, saves.get());
        assertTrue(saver.isArmed());
    }

    @Test
    void remembersDestinationOwnerThatArrivesBeforeNextUiTick() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        AtomicInteger saves = new AtomicInteger();

        saver.onAction("travel", "hearth");
        saver.onTerritoryUpdated(true);
        saver.onTeleportStarted();
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });

        assertEquals(1, saves.get());
        assertFalse(saver.isArmed());
    }

    @Test
    void laterOwnerUpdateRestartsTheSettleWindow() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        AtomicInteger saves = new AtomicInteger();

        saver.onAction("travel", "hearth");
        saver.onTeleportStarted();
        saver.onTerritoryUpdated(true);
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS * 0.75, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });
        saver.onTerritoryUpdated(true);
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS * 0.75, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });
        assertEquals(0, saves.get());

        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS, () -> true, () -> {
            saves.incrementAndGet();
            return true;
        });
        assertEquals(1, saves.get());
    }

    @Test
    void keepsRetryingWhileTerritoryResourcesAreStillLoading() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicInteger attempts = new AtomicInteger();

        saver.onAction("travel", "hearth");
        saver.onTeleportStarted();
        saver.onTerritoryUpdated(true);
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS, () -> true, () -> {
            attempts.incrementAndGet();
            return ready.get();
        });
        assertEquals(1, attempts.get());
        assertTrue(saver.isArmed());

        ready.set(true);
        saver.tick(HearthHomeAutoSaver.RETRY_SECONDS, () -> true, () -> {
            attempts.incrementAndGet();
            return ready.get();
        });
        assertEquals(2, attempts.get());
        assertFalse(saver.isArmed());
    }

    @Test
    void givesUpWhenNoDestinationTerritoryArrives() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        AtomicInteger attempts = new AtomicInteger();

        saver.onAction("travel", "hearth");
        saver.onTeleportStarted();
        saver.onTerritoryUpdated(true);
        saver.tick(HearthHomeAutoSaver.TIMEOUT_SECONDS + 1, () -> true, () -> {
            attempts.incrementAndGet();
            return false;
        });

        assertFalse(saver.isArmed());
        assertEquals(0, attempts.get());
    }

    @Test
    void saveFailureDoesNotEscapeIntoUiTick() {
        HearthHomeAutoSaver saver = new HearthHomeAutoSaver();
        AtomicInteger attempts = new AtomicInteger();

        saver.onAction("travel", "hearth");
        saver.onTeleportStarted();
        saver.onTerritoryUpdated(true);
        saver.tick(HearthHomeAutoSaver.SETTLE_SECONDS, () -> true, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("injected");
        });

        assertEquals(1, attempts.get());
        assertTrue(saver.isArmed());
    }
}
