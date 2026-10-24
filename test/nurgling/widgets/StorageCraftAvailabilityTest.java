package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StorageCraftAvailabilityTest {
    @Test void lateReplyFromOldOriginOrUnknownVillageCannotRestoreStaleHint() {
        StorageCraftAvailability hints = new StorageCraftAvailability();
        Object session = new Object(), db = new Object();
        hints.updateContext(Set.of("Barley Seed"), session, "grid-A", db, true);
        assertEquals(StorageCraftAvailability.Status.CHECKING, hints.status("Barley Seed"));
        long first = hints.generation();
        hints.applyAnswer(first, Map.of("Barley Seed", 2));
        assertEquals(2, hints.count("Barley Seed"));
        assertEquals(StorageCraftAvailability.Status.PRESENT, hints.status("Barley Seed"));

        hints.updateContext(Set.of("Barley Seed"), session, "grid-B", db, true);
        hints.applyAnswer(first, Map.of("Barley Seed", 4));
        assertEquals(0, hints.count("Barley Seed"));
        assertEquals(StorageCraftAvailability.Status.CHECKING, hints.status("Barley Seed"));
        long second = hints.generation();
        hints.applyAnswer(second, Map.of("Barley Seed", 1));
        assertEquals(1, hints.count("Barley Seed"));

        hints.updateContext(Set.of("Barley Seed"), session, null, db, false);
        hints.applyAnswer(second, Map.of("Barley Seed", 7));
        assertEquals(0, hints.count("Barley Seed"));
        assertEquals(StorageCraftAvailability.Status.UNAVAILABLE, hints.status("Barley Seed"));
    }

    @Test void confirmedEmptyAndFailedQueryHaveDifferentStatus() {
        StorageCraftAvailability hints = new StorageCraftAvailability();
        Object session = new Object(), db = new Object();
        hints.updateContext(Set.of("Barley Seed"), session, "grid-A", db, true);
        long generation = hints.generation();
        hints.applyAnswer(generation, Map.of());
        assertEquals(StorageCraftAvailability.Status.EMPTY, hints.status("Barley Seed"));
        assertEquals(0, hints.count("Barley Seed"));

        hints.applyFailure(generation);
        assertEquals(StorageCraftAvailability.Status.UNAVAILABLE, hints.status("Barley Seed"));
        assertEquals(0, hints.count("Barley Seed"));

        hints.updateContext(Set.of("Barley Seed"), session, "grid-B", db, true);
        hints.applyFailure(generation);
        assertEquals(StorageCraftAvailability.Status.CHECKING, hints.status("Barley Seed"));
        hints.applyAnswer(hints.generation(), Map.of("Barley Seed", 3));
        assertEquals(StorageCraftAvailability.Status.PRESENT, hints.status("Barley Seed"));
    }
}
