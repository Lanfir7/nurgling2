package nurgling.db;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerInventorySyncTest {
    private static final Map<String, String> CAPS = new LinkedHashMap<>();
    static {
        CAPS.put("gfx/terobjs/chest", "Chest");
        CAPS.put("gfx/terobjs/map/hiddenhollow", "Hidden Hollow");
        CAPS.put("gfx/terobjs/wbasket", "Basket");
        CAPS.put("gfx/terobjs/thatchbasket", "Basket");
    }

    @Test
    void prefersAlreadyBoundGob() {
        assertEquals("bound", ContainerInventorySync.resolveParentGob("bound", "last"));
    }

    @Test
    void fallsBackToLastActionGob() {
        assertEquals("last", ContainerInventorySync.resolveParentGob(null, "last"));
        assertNull(ContainerInventorySync.resolveParentGob(null, null));
    }

    @Test
    void pickGobIgnoresLastActionChestForHiddenHollow() {
        assertEquals("hollow", ContainerInventorySync.pickGob(
                "chest", false, "chest", false, "hollow"));
    }

    @Test
    void pickGobKeepsMatchingBoundGob() {
        assertEquals("hollow", ContainerInventorySync.pickGob(
                "hollow", true, "chest", false, null));
    }

    @Test
    void hiddenHollowCapMapsToMapObject() {
        assertEquals(List.of("gfx/terobjs/map/hiddenhollow"),
                ContainerInventorySync.resourceNamesForCap("Hidden Hollow", CAPS));
    }

    @Test
    void sharedCaptionReturnsEveryMatchingResource() {
        assertEquals(List.of("gfx/terobjs/wbasket", "gfx/terobjs/thatchbasket"),
                ContainerInventorySync.resourceNamesForCap("Basket", CAPS));
    }

    @Test
    void gobMustMatchWindowCaption() {
        assertTrue(ContainerInventorySync.gobMatchesWindow(
                "gfx/terobjs/map/hiddenhollow", "Hidden Hollow", CAPS));
        assertFalse(ContainerInventorySync.gobMatchesWindow(
                "gfx/terobjs/chest", "Hidden Hollow", CAPS));
    }

    @Test
    void writesWhenSessionSawItemsEvenIfNowEmpty() {
        assertTrue(ContainerInventorySync.shouldWrite(3, 3));
        assertTrue(ContainerInventorySync.shouldWrite(0, 5));
    }

    @Test
    void skipsEmptyWriteWhenNothingWasCollected() {
        assertFalse(ContainerInventorySync.shouldWrite(0, 0));
        assertFalse(ContainerInventorySync.shouldWrite(false));
    }

    @Test
    void liveWidgetsReplaceEventCacheOnClose() {
        assertEquals(List.of("current"),
                ContainerInventorySync.itemsToPersist(List.of("stale-nettle"), List.of("current"), 1));
    }

    @Test
    void teardownWithEmptyWidgetsKeepsSessionCache() {
        assertEquals(List.of("stale-nettle"),
                ContainerInventorySync.itemsToPersist(List.of("stale-nettle"), List.of(), 0));
    }

    @Test
    void emptyLiveWhileSlotsExistKeepsSessionCache() {
        assertEquals(List.of("chest-item"),
                ContainerInventorySync.itemsToPersist(List.of("chest-item"), List.of(), 3));
    }

    @Test
    void unexpandedStackIsDroppedWhenAmountNoLongerMatches() {
        assertFalse(ContainerInventorySync.keepUnexpandedStackEntries(16, 0));
        assertFalse(ContainerInventorySync.keepUnexpandedStackEntries(16, 5));
        assertTrue(ContainerInventorySync.keepUnexpandedStackEntries(16, 16));
    }
}
