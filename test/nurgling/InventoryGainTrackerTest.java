package nurgling;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryGainTrackerTest {
    private static InventoryGainTracker.Item<Object> item(Object identity, String resource, int amount) {
        return new InventoryGainTracker.Item<>(identity, resource, amount);
    }

    @Test
    void initialInventoryAndDelayedResourceResolutionAreNotLoot() {
        InventoryGainTracker<Object> tracker = new InventoryGainTracker<>();
        Object existing = new Object();
        assertTrue(tracker.observe(List.of(item(existing, null, 1)), 0.1).isEmpty());
        assertTrue(tracker.observe(List.of(item(existing, "gfx/invobjs/ore", 1)), 0.5).isEmpty());
        assertTrue(tracker.observe(List.of(item(existing, "gfx/invobjs/ore", 1)), 0.5).isEmpty());
    }

    @Test
    void newItemAndStackGrowthBecomeOnePositiveGain() {
        InventoryGainTracker<Object> tracker = new InventoryGainTracker<>();
        tracker.observe(List.of(), 0.1);
        Object stack = new Object();
        assertTrue(tracker.observe(List.of(item(stack, "gfx/invobjs/ore", 1)), 0.1).isEmpty());
        assertTrue(tracker.observe(List.of(item(stack, "gfx/invobjs/ore", 4)), 0.1).isEmpty());
        List<InventoryGainTracker.Gain<Object>> gains = tracker.observe(List.of(item(stack, "gfx/invobjs/ore", 4)), 0.36);
        assertEquals(1, gains.size());
        assertEquals(4, gains.get(0).amount);
        assertSame(stack, gains.get(0).example);
    }

    @Test
    void sameResourceMoveAcrossTwoSnapshotsDoesNotAnnounce() {
        InventoryGainTracker<Object> tracker = new InventoryGainTracker<>();
        Object old = new Object();
        Object replacement = new Object();
        tracker.observe(List.of(item(old, "ore", 1)), 0.1);
        assertTrue(tracker.observe(List.of(), 0.1).isEmpty());
        assertTrue(tracker.observe(List.of(item(replacement, "ore", 1)), 0.1).isEmpty());
        assertTrue(tracker.observe(List.of(item(replacement, "ore", 1)), 0.5).isEmpty());
    }

    @Test
    void movedStackAndSeparateNewResourceCountOnlyRealGain() {
        InventoryGainTracker<Object> tracker = new InventoryGainTracker<>();
        Object old = new Object();
        Object moved = new Object();
        Object wheat = new Object();
        tracker.observe(List.of(item(old, "ore", 5)), 0.1);
        assertTrue(tracker.observe(List.of(item(moved, "ore", 5), item(wheat, "wheat", 2)), 0.1).isEmpty());
        List<InventoryGainTracker.Gain<Object>> gains = tracker.observe(List.of(item(moved, "ore", 5), item(wheat, "wheat", 2)), 0.4);
        assertEquals(1, gains.size());
        assertEquals("wheat", gains.get(0).resource);
        assertEquals(2, gains.get(0).amount);
    }

    @Test
    void newlySeenUnresolvedItemAnnouncesAfterNameAndResourceArrive() {
        InventoryGainTracker<Object> tracker = new InventoryGainTracker<>();
        Object newItem = new Object();
        tracker.observe(List.of(), 0.1);
        assertTrue(tracker.observe(List.of(item(newItem, null, 1)), 0.1).isEmpty());
        assertTrue(tracker.observe(List.of(item(newItem, "wheat", 1)), 0.1).isEmpty());
        List<InventoryGainTracker.Gain<Object>> gains = tracker.observe(List.of(item(newItem, "wheat", 1)), 0.4);
        assertEquals(1, gains.size());
        assertEquals(1, gains.get(0).amount);
    }

    @Test
    void continuousPickupStreamPublishesBeforeItStops() {
        InventoryGainTracker<Object> tracker = new InventoryGainTracker<>();
        Object stack = new Object();
        tracker.observe(List.of(), 0.1);
        for (int count = 1; count < 5; count++)
            assertTrue(tracker.observe(List.of(item(stack, "ore", count)), 0.2).isEmpty());
        List<InventoryGainTracker.Gain<Object>> gains = tracker.observe(List.of(item(stack, "ore", 5)), 0.2);
        assertEquals(1, gains.size());
        assertEquals(5, gains.get(0).amount);
    }
}
