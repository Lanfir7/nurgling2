package haven.render;

import haven.Loading;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DeferredRenderSlotsTest {
    @Test void equalButDistinctSlotsStayIndependentIncludingRemoval() {
        Object first = new String("same"), second = new String("same");
        DeferredRenderSlots<Object, Integer> slots = new DeferredRenderSlots<>(
                k -> k == first ? 1 : 2, (old, next) -> {});
        slots.add(first, true);
        slots.add(second, true);
        slots.remove(first);
        slots.retry(2, Long.MAX_VALUE);
        assertFalse(slots.contains(first));
        assertEquals(Integer.valueOf(2), slots.get(second));
    }

    @Test void additionsDoNotPrepareOnObjectUpdateThread() {
        AtomicInteger calls = new AtomicInteger();
        DeferredRenderSlots<Object, Integer> slots = new DeferredRenderSlots<>(
                k -> calls.incrementAndGet(), (old, next) -> {});
        Object key = new Object();
        slots.add(key, true);
        assertEquals(0, calls.get());
        assertNull(slots.get(key));
        slots.retry(1, Long.MAX_VALUE);
        assertEquals(1, calls.get());
        assertEquals(Integer.valueOf(1), slots.get(key));
    }

    @Test void removedPendingObjectIsNotResurrected() {
        AtomicInteger calls = new AtomicInteger();
        DeferredRenderSlots<Object, Integer> slots = new DeferredRenderSlots<>(
                k -> calls.incrementAndGet(), (old, next) -> {});
        Object key = new Object();
        slots.add(key, true);
        slots.remove(key);
        slots.retry(10, Long.MAX_VALUE);
        assertEquals(0, calls.get());
        assertFalse(slots.contains(key));
    }

    @Test void pendingReplacementKeepsOldDrawableUntilReady() {
        List<String> installed = new ArrayList<>();
        boolean[] ready = {true};
        int[] value = {1};
        DeferredRenderSlots<Object, Integer> slots = new DeferredRenderSlots<>(
                k -> { if(!ready[0]) throw new Loading(); return value[0]; },
                (old, next) -> installed.add(old + "->" + next));
        Object key = new Object();
        slots.add(key, false);
        ready[0] = false;
        value[0] = 2;
        slots.update(key, true);
        slots.retry(1, Long.MAX_VALUE);
        assertEquals(Integer.valueOf(1), slots.get(key));
        assertEquals(Collections.singletonList("null->1"), installed);
        ready[0] = true;
        slots.retry(1, Long.MAX_VALUE);
        assertEquals(Integer.valueOf(2), slots.get(key));
        assertEquals(Arrays.asList("null->1", "1->2"), installed);
    }

    @Test void slowFirstSlotDoesNotStarveReadySlots() {
        Object slow = new Object(), fast = new Object();
        DeferredRenderSlots<Object, Integer> slots = new DeferredRenderSlots<>(
                k -> { if(k == slow) throw new Loading(); return 7; }, (old, next) -> {});
        slots.add(slow, true);
        slots.add(fast, true);
        slots.retry(1, Long.MAX_VALUE);
        slots.retry(1, Long.MAX_VALUE);
        assertEquals(Integer.valueOf(7), slots.get(fast));
        assertNull(slots.get(slow));
    }

    @Test void refreshUsesLatestStateAndRemovalReleasesExactlyOnce() {
        int[] value = {1};
        List<String> installed = new ArrayList<>();
        DeferredRenderSlots<Object, Integer> slots = new DeferredRenderSlots<>(
                k -> value[0], (old, next) -> installed.add(old + "->" + next));
        Object key = new Object();
        slots.add(key, true);
        value[0] = 2;
        slots.refresh();
        slots.retry(1, Long.MAX_VALUE);
        slots.remove(key);
        slots.retry(5, Long.MAX_VALUE);
        assertEquals(Arrays.asList("null->2", "2->null"), installed);
        assertThrows(IllegalStateException.class, () -> slots.remove(key));
    }

    @Test void realPreparationFailureIsNotTreatedAsStillLoading() {
        IllegalArgumentException failure = new IllegalArgumentException("bad model");
        DeferredRenderSlots<Object, Integer> slots = new DeferredRenderSlots<>(
                k -> { throw failure; }, (old, next) -> {});
        slots.add(new Object(), true);
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> slots.retry(1, Long.MAX_VALUE)));
    }

    @Test void duplicateAddsFailEvenWhenFirstSlotIsPending() {
        DeferredRenderSlots<Object, Integer> slots = new DeferredRenderSlots<>(
                k -> 1, (old, next) -> {});
        Object key = new Object();
        slots.add(key, true);
        assertThrows(IllegalStateException.class, () -> slots.add(key, true));
    }
}
