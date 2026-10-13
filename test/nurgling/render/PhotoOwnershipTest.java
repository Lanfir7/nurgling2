package nurgling.render;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PhotoOwnershipTest {
    @AfterEach void clear() { Photo.stop(); }

    @Test void switchingViewsRestoresPriorOwnerAndScopesEffects() {
        Object mapA = new Object(), mapB = new Object();
        AtomicInteger restoreA = new AtomicInteger(), restoreB = new AtomicInteger();

        Photo.enter(mapA, () -> {
            assertTrue(Photo.active(mapA));
            restoreA.incrementAndGet();
            Photo.leave(mapA);
        });
        assertTrue(Photo.active(mapA));
        assertFalse(Photo.active(mapB));

        Photo.enter(mapB, () -> {
            assertTrue(Photo.active(mapB));
            restoreB.incrementAndGet();
            Photo.leave(mapB);
        });
        assertEquals(1, restoreA.get());
        assertFalse(Photo.active(mapA));
        assertTrue(Photo.active(mapB));

        Photo.stop();
        assertEquals(1, restoreB.get());
        assertFalse(Photo.active(mapA));
        assertFalse(Photo.active(mapB));
    }

    @Test void leavingAnotherViewDoesNotDisableOwner() {
        Object mapA = new Object(), mapB = new Object();
        AtomicInteger restored = new AtomicInteger();
        Photo.enter(mapA, restored::incrementAndGet);

        Photo.leave(mapB);
        assertTrue(Photo.active(mapA));
        assertEquals(0, restored.get());
    }
}
