package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ForageMarkerLogicTest {
    @Test
    void pickAcceptedPickUpRejected() {
        assertTrue(ForageMarkerLogic.isPickAction("Pick"));
        assertFalse(ForageMarkerLogic.isPickAction("Pick up"));
        assertFalse(ForageMarkerLogic.isPickAction("Harvest"));
        assertFalse(ForageMarkerLogic.isPickAction(null));
    }

    @Test
    void gardenPotByName() {
        assertTrue(ForageMarkerLogic.isGardenPot("gfx/terobjs/gardenpot"));
        assertTrue(ForageMarkerLogic.isGardenPot("GardenPot"));
        assertFalse(ForageMarkerLogic.isGardenPot("gfx/terobjs/herbs/blueberry"));
        assertFalse(ForageMarkerLogic.isGardenPot(null));
    }

    @Test
    void qualityThreshold() {
        assertTrue(ForageMarkerLogic.shouldPlace(40f, 40));
        assertTrue(ForageMarkerLogic.shouldPlace(40.1f, 40));
        assertTrue(ForageMarkerLogic.shouldPlace(43f, 40));
        assertFalse(ForageMarkerLogic.shouldPlace(39.9f, 40));
        assertFalse(ForageMarkerLogic.shouldPlace(43f, 50));
        assertFalse(ForageMarkerLogic.shouldPlace(null, 10));
    }

    @Test
    void minQualityClampedToSliderRange() {
        assertEquals(10, ForageMarkerLogic.clampMinQuality(5));
        assertEquals(10, ForageMarkerLogic.clampMinQuality(10));
        assertEquals(40, ForageMarkerLogic.clampMinQuality(40));
        assertEquals(100, ForageMarkerLogic.clampMinQuality(100));
        assertEquals(100, ForageMarkerLogic.clampMinQuality(150));
    }

    @Test
    void minQualityFromConfig() {
        assertEquals(40, ForageMarkerLogic.minQualityFromConfig(null));
        assertEquals(40, ForageMarkerLogic.minQualityFromConfig(40));
        assertEquals(10, ForageMarkerLogic.minQualityFromConfig(10.0));
        assertEquals(10, ForageMarkerLogic.minQualityFromConfig(1));
        assertEquals(100, ForageMarkerLogic.minQualityFromConfig(999));
    }

    @Test
    void resolveItemNamePrefersDisplayThenTooltip() {
        assertEquals("Blueberries", ForageMarkerLogic.resolveItemName("Blueberries", "tt", "gfx/herbs/blueberry"));
        assertEquals("Blueberry", ForageMarkerLogic.resolveItemName(null, "Blueberry", "gfx/herbs/blueberry"));
        assertEquals("blueberry", ForageMarkerLogic.resolveItemName(null, null, "gfx/herbs/blueberry"));
        assertNull(ForageMarkerLogic.resolveItemName(null, null, null));
    }

    @Test
    void forageIdPrefix() {
        assertTrue(ForageMarkerLogic.isForageId("forage_1_2_3_Blueberries"));
        assertFalse(ForageMarkerLogic.isForageId("animal_1"));
        assertFalse(ForageMarkerLogic.isForageId("labeled_1_2_3_q20"));
        assertFalse(ForageMarkerLogic.isForageId(null));
    }

    @Test
    void forageLocationIdsSnapshotsOnlyForageMarks() {
        java.util.List<String> ids = ForageMarkerLogic.forageLocationIds(java.util.Arrays.asList(
            "forage_1_2_3_Blueberries",
            "labeled_1_2_3_q20",
            "animal_7",
            "forage_9_10_11_Morels",
            null));
        assertEquals(java.util.Arrays.asList(
            "forage_1_2_3_Blueberries",
            "forage_9_10_11_Morels"), ids);
        assertTrue(ForageMarkerLogic.forageLocationIds(null).isEmpty());
        assertTrue(ForageMarkerLogic.forageLocationIds(java.util.Collections.emptyList()).isEmpty());
    }

    @Test
    void labelFormatAndParse() {
        assertEquals("q45", ForageMarkerLogic.formatLabel(45.4));
        assertEquals(45.0, ForageMarkerLogic.parseQuality("q45"), 0.01);
        assertEquals(0.0, ForageMarkerLogic.parseQuality("nope"), 0.01);
        assertEquals(0.0, ForageMarkerLogic.parseQuality(null), 0.01);
    }

    @Test
    void forageLocationIdStartsWithPrefix() {
        String id = ForageMarkerLogic.forageLocationId(9, 1, 2, "Blueberries");
        assertTrue(id.startsWith("forage_"));
        assertTrue(id.contains("Blueberries") || id.contains("Blueberr"));
    }

    @Test
    void relocatedForageIdKeepsPrefixAndType() {
        String id = "forage_1_100_200_Blueberries_171000";
        String moved = ForageMarkerLogic.relocatedForageId(id, 9L, 50, -10);
        assertEquals("forage_9_50_-10_Blueberries_171000", moved);
        assertTrue(ForageMarkerLogic.isForageId(moved));
        assertEquals("animal_7", ForageMarkerLogic.relocatedForageId("animal_7", 9L, 1, 2));
    }

    @Test
    void queuedSaveStopsAfterShutdown() {
        assertTrue(ForageMarkerLogic.allowQueuedMarkSave(false));
        assertFalse(ForageMarkerLogic.allowQueuedMarkSave(true));
    }

    @Test
    void persistForageIdRestoresPrefix() {
        assertEquals("forage_1_2_3_x", ForageMarkerLogic.persistForageId("forage_1_2_3_x", false));
        assertEquals("forage_1_2_3_x", ForageMarkerLogic.persistForageId("forage_1_2_3_x", true));
        assertEquals("forage_labeled_1_2_3_q20", ForageMarkerLogic.persistForageId("labeled_1_2_3_q20", true));
        assertEquals("labeled_1_2_3_q20", ForageMarkerLogic.persistForageId("labeled_1_2_3_q20", false));
        assertNull(ForageMarkerLogic.persistForageId(null, true));
    }

    @Test
    void mapSearchNameOrQuality() {
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", ""));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "blue"));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "q45"));
        assertTrue(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "45"));
        assertFalse(ForageMarkerLogic.matchesMapSearch("Blueberries", "q45", "chantrelle"));
    }

    @Test
    void windowSearchTypeAndMinQuality() {
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", null));
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Blueberries", 40.0));
        assertTrue(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", 45.0));
        assertFalse(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Chantrelles", null));
        assertFalse(ForageMarkerLogic.matchesWindowSearch("Blueberries", "q45", "Any", 46.0));
    }

    @Test
    void dedupKeepsHigherQualityInsideRadiusList() {
        ForageMarkerLogic.Neighbor weak = new ForageMarkerLogic.Neighbor("forage_old", 30);
        ForageMarkerLogic.Dedup better = ForageMarkerLogic.decideDedup(50, Collections.singletonList(weak));
        assertFalse(better.skip);
        assertEquals(Collections.singletonList("forage_old"), better.removeIds);

        ForageMarkerLogic.Neighbor strong = new ForageMarkerLogic.Neighbor("forage_hi", 60);
        ForageMarkerLogic.Dedup worse = ForageMarkerLogic.decideDedup(50, Collections.singletonList(strong));
        assertTrue(worse.skip);
        assertTrue(worse.removeIds.isEmpty());

        ForageMarkerLogic.Dedup many = ForageMarkerLogic.decideDedup(50, Arrays.asList(
                new ForageMarkerLogic.Neighbor("a", 20),
                new ForageMarkerLogic.Neighbor("b", 35)));
        assertFalse(many.skip);
        assertEquals(Arrays.asList("a", "b"), many.removeIds);

        ForageMarkerLogic.Dedup none = ForageMarkerLogic.decideDedup(50, Collections.emptyList());
        assertFalse(none.skip);
        assertTrue(none.removeIds.isEmpty());
    }

    @Test
    void anyInventoryAcceptedDuringPick() {
        assertTrue(ForageMarkerLogic.acceptsPickedItemInventory(true, false));
        assertTrue(ForageMarkerLogic.acceptsPickedItemInventory(true, true));
        assertTrue(ForageMarkerLogic.acceptsPickedItemInventory(false, false));
        assertTrue(ForageMarkerLogic.acceptsPickedItemInventory(false, true));
    }

    @Test
    void stackContainerIsNotWatched() {
        assertTrue(ForageMarkerLogic.isLikelyStackContainer(false, true, false, null));
        assertTrue(ForageMarkerLogic.isLikelyStackContainer(false, false, true, null));
        assertFalse(ForageMarkerLogic.isLikelyStackContainer(true, false, false, 25f));
        assertFalse(ForageMarkerLogic.isLikelyStackContainer(false, false, false, 25f));
    }

    @Test
    void stackWatchKeepsGoodCandidateAndSkipsWrapper() {
        assertFalse(ForageMarkerLogic.shouldWatchIncoming(true, false, null, false, null, 20));
        assertTrue(ForageMarkerLogic.shouldWatchIncoming(false, false, 25f, false, null, 20));
        assertTrue(ForageMarkerLogic.shouldWatchIncoming(false, false, null, false, null, 20));
        assertFalse(ForageMarkerLogic.shouldWatchIncoming(false, false, 12f, false, null, 20));
        assertFalse(ForageMarkerLogic.shouldWatchIncoming(false, false, 12f, true, null, 20));
        assertTrue(ForageMarkerLogic.shouldWatchIncoming(false, false, 40f, true, null, 20));
        assertFalse(ForageMarkerLogic.shouldWatchIncoming(false, false, 12f, true, 25f, 20));
        assertTrue(ForageMarkerLogic.shouldWatchIncoming(false, false, 25f, true, 12f, 20));
        assertFalse(ForageMarkerLogic.shouldWatchIncoming(false, false, 40f, true, 25f, 20));
        assertTrue(ForageMarkerLogic.shouldWatchIncoming(false, true, 25f, true, 40f, 20));
    }

    @Test
    void firstPickStillPlacesAfterSecondPickStarts() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 10, 20, 0);
        assertTrue(s.offerItem("a", false, false, null, 20, 0));
        s.notePick(1, 30, 40, 100);
        ForageMarkerLogic.Place first = s.placeTick("a", false, 45f, "Blueberries", 20, 200);
        assertNotNull(first);
        assertEquals(10, first.tileX);
        assertEquals(20, first.tileY);
        assertTrue(s.offerItem("b", false, false, 50f, 20, 300));
        ForageMarkerLogic.Place second = s.placeTick("b", false, 50f, "Blueberries", 20, 400);
        assertNotNull(second);
        assertEquals(30, second.tileX);
        assertEquals(40, second.tileY);
    }

    @Test
    void consecutivePicksCanResolveWhenItemsArriveInReverseOrder() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 10, 20, "gfx/terobjs/herbs/dandelion", 0);
        s.notePick(1, 30, 40, "gfx/terobjs/herbs/rustroot", 10);

        assertTrue(s.offerItem("second", false, false, 50f,
            "gfx/invobjs/rustroot", 20, 20));
        assertTrue(s.offerItem("first", false, false, 45f,
            "gfx/invobjs/dandelion", 20, 30));

        ForageMarkerLogic.Place second = s.placeTick("second", false, 50f,
            "Rustroot", "gfx/invobjs/rustroot", 20, 40);
        ForageMarkerLogic.Place first = s.placeTick("first", false, 45f,
            "Dandelion", "gfx/invobjs/dandelion", 20, 50);

        assertNotNull(second);
        assertNotNull(first);
        assertEquals(30, second.tileX);
        assertEquals(40, second.tileY);
        assertEquals(10, first.tileX);
        assertEquals(20, first.tileY);
    }

    @Test
    void pickedItemStillPlacesWhenTooltipArrivesAfterFiveSeconds() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 12, 34, 0);
        assertTrue(s.offerItem("berry", false, false, null, 20, 10));

        ForageMarkerLogic.Place placed = s.placeTick("berry", false, 45f,
            "Blueberries", 20, 6_000);

        assertNotNull(placed);
        assertEquals(12, placed.tileX);
        assertEquals(34, placed.tileY);
    }

    @Test
    void otherTypeKeepsOwnCoordsInsideDedupRadius() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 100, 100, 0);
        assertTrue(s.offerItem("chantrelle", false, false, 55f, 20, 10));
        ForageMarkerLogic.Place a = s.placeTick("chantrelle", false, 55f, "Chantrelles", 20, 20);
        assertEquals(100, a.tileX);
        s.notePick(1, 105, 105, 30);
        assertTrue(s.offerItem("berry", false, false, 60f, 20, 40));
        ForageMarkerLogic.Place b = s.placeTick("berry", false, 60f, "Blueberries", 20, 50);
        assertEquals(105, b.tileX);
        assertEquals(105, b.tileY);
        assertNotEquals(a.tileX, b.tileX);
    }

    @Test
    void staleUnboundIsReplacedByNextPick() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 1, 1, 0);
        s.notePick(1, 9, 9, 50);
        assertTrue(s.offerItem("x", false, false, 40f, 20, 60));
        ForageMarkerLogic.Place p = s.placeTick("x", false, 40f, "Blueberries", 20, 70);
        assertEquals(9, p.tileX);
        assertEquals(9, p.tileY);
    }

    @Test
    void stackWrapperHandsCoordsToMember() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 7, 8, 0);
        assertFalse(s.offerItem("wrap", true, false, null, 20, 10));
        assertTrue(s.offerItem("member", false, true, 44f, 20, 30));
        ForageMarkerLogic.Place p = s.placeTick("member", false, 44f, "Blueberries", 20, 40);
        assertEquals(7, p.tileX);
        assertEquals(8, p.tileY);
    }

    @Test
    void lateDetectedStackWrapperReturnsPickToItsMember() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 7, 8, "gfx/terobjs/herbs/dandelion", 0);

        assertTrue(s.offerItem("wrap", false, false, null,
            "gfx/invobjs/dandelion", 20, 10));
        assertNull(s.placeTick("wrap", true, null, null,
            "gfx/invobjs/dandelion", 20, 20));

        assertTrue(s.offerItem("member", false, true, 44f,
            "gfx/invobjs/dandelion", 20, 30));
        ForageMarkerLogic.Place placed = s.placeTick("member", false, 44f, "Dandelion",
            "gfx/invobjs/dandelion", 20, 40);
        assertNotNull(placed);
        assertEquals(7, placed.tileX);
        assertEquals(8, placed.tileY);
    }

    @Test
    void rebuiltStackUsesNewMemberInsteadOfOldQuality() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 207, 208, "gfx/terobjs/herbs/dandelion", 0);

        assertTrue(s.offerItem("wrap", false, false, 47f,
            "gfx/invobjs/dandelion", 10, 10));
        assertTrue(s.offerItem("old-member", false, true, 47f,
            "gfx/invobjs/dandelion", 10, 20));
        assertTrue(s.offerItem("new-member", false, true, 16f,
            "gfx/invobjs/dandelion", 10, 30));

        assertNull(s.placeTick("wrap", true, 47f, "Dandelion",
            "gfx/invobjs/dandelion", 10, 40));
        assertNull(s.placeTick("old-member", false, 47f, "Dandelion",
            "gfx/invobjs/dandelion", 10, 40));
        ForageMarkerLogic.Place placed = s.placeTick("new-member", false, 16f, "Dandelion",
            "gfx/invobjs/dandelion", 10, 40);
        assertNotNull(placed);
        assertEquals(207, placed.tileX);
        assertEquals(208, placed.tileY);
    }

    @Test
    void mismatchedItemDoesNotConsumePendingPick() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 7, 8, "gfx/terobjs/herbs/dandelion", 0);

        assertFalse(s.offerItem("wrong", false, false, 50f,
            "gfx/invobjs/rustroot", 20, 10));
        assertNull(s.placeTick("wrong", false, 50f, "Rustroot",
            "gfx/invobjs/rustroot", 20, 20));

        assertTrue(s.offerItem("right", false, false, 45f,
            "gfx/invobjs/dandelion", 20, 30));
        ForageMarkerLogic.Place placed = s.placeTick("right", false, 45f,
            "Dandelion", "gfx/invobjs/dandelion", 20, 40);
        assertNotNull(placed);
        assertEquals(7, placed.tileX);
        assertEquals(8, placed.tileY);
    }

    @Test
    void candidateWaitsUntilItsResourceIdentityLoads() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 11, 12, "gfx/terobjs/herbs/dandelion", 0);
        assertTrue(s.offerItem("item", false, false, null, null, 20, 10));

        assertNull(s.placeTick("item", false, 45f, "Dandelion", null, 20, 20));
        assertTrue(s.isWatching("item"));

        ForageMarkerLogic.Place placed = s.placeTick("item", false, 45f, "Dandelion",
            "gfx/invobjs/dandelion", 20, 30);
        assertNotNull(placed);
        assertEquals(11, placed.tileX);
        assertEquals(12, placed.tileY);
    }

    @Test
    void sameResourcePicksStayFifoEvenWhenTicksArriveInReverseOrder() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.notePick(1, 10, 20, "gfx/terobjs/herbs/dandelion", 0);
        s.notePick(1, 30, 40, "gfx/terobjs/herbs/dandelion", 10);
        assertTrue(s.offerItem("first", false, false, 45f,
            "gfx/invobjs/dandelion", 20, 20));
        assertTrue(s.offerItem("second", false, false, 50f,
            "gfx/invobjs/dandelion", 20, 30));

        ForageMarkerLogic.Place second = s.placeTick("second", false, 50f, "Dandelion",
            "gfx/invobjs/dandelion", 20, 40);
        ForageMarkerLogic.Place first = s.placeTick("first", false, 45f, "Dandelion",
            "gfx/invobjs/dandelion", 20, 50);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(10, first.tileX);
        assertEquals(20, first.tileY);
        assertEquals(30, second.tileX);
        assertEquals(40, second.tileY);
    }

    @Test
    void caughtCritterUsesWhereItWasFirstSeenInsteadOfWhereItWasCaught() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.noteCritterSeen(77L, 3L, 100, 200,
            "gfx/kritter/dragonfly/dragonfly", 0);
        s.noteCritterSeen(77L, 3L, 140, 260,
            "gfx/kritter/dragonfly/dragonfly", 1_000);
        assertTrue(s.noteCritterInteraction(77L, 2_000));

        assertTrue(s.offerItem("caught", false, false, 45f,
            "gfx/invobjs/dragonfly-emerald", 10, 2_100));
        ForageMarkerLogic.Place placed = s.placeTick("caught", false, 45f,
            "Emerald Dragonfly", "gfx/invobjs/dragonfly-emerald", 10, 2_200);

        assertNotNull(placed);
        assertEquals(3L, placed.segmentId);
        assertEquals(100, placed.tileX);
        assertEquals(200, placed.tileY);
    }

    @Test
    void interactionSelectsTheSeenAnimalAmongSeveralOfTheSameType() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.noteCritterSeen(10L, 1L, 10, 20,
            "gfx/kritter/rabbit/rabbit", 0);
        s.noteCritterSeen(20L, 1L, 80, 90,
            "gfx/kritter/rabbit/rabbit", 10);
        assertTrue(s.noteCritterInteraction(20L, 20));

        assertTrue(s.offerItem("rabbit", false, false, 30f,
            "gfx/invobjs/rabbit", 10, 30));
        ForageMarkerLogic.Place placed = s.placeTick("rabbit", false, 30f,
            "Rabbit", "gfx/invobjs/rabbit", 10, 40);

        assertNotNull(placed);
        assertEquals(80, placed.tileX);
        assertEquals(90, placed.tileY);
    }

    @Test
    void caughtCritterRemainsPendingDuringAChaseLongerThanItemQualityTimeout() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.noteCritterSeen(77L, 3L, 100, 200,
            "gfx/kritter/rabbit/rabbit", 0);
        assertTrue(s.noteCritterInteraction(77L, 1_000));

        assertTrue(s.offerItem("rabbit", false, false, 30f,
            "gfx/invobjs/rabbit", 10, 61_000));
        ForageMarkerLogic.Place placed = s.placeTick("rabbit", false, 30f,
            "Rabbit", "gfx/invobjs/rabbit", 10, 61_100);

        assertNotNull(placed);
        assertEquals(100, placed.tileX);
        assertEquals(200, placed.tileY);
    }

    @Test
    void caughtChickenMatchesSexSpecificInventoryResource() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.noteCritterSeen(88L, 3L, 120, 220,
            "gfx/kritter/chicken/chicken", 0);
        assertTrue(s.noteCritterInteraction(88L, 1_000));

        assertTrue(s.offerItem("hen", false, false, 30f,
            "gfx/invobjs/hen", 10, 1_100));
        ForageMarkerLogic.Place placed = s.placeTick("hen", false, 30f,
            "Hen", "gfx/invobjs/hen", 10, 1_200);

        assertNotNull(placed);
        assertEquals(120, placed.tileX);
        assertEquals(220, placed.tileY);
    }

    @Test
    void merelySeeingCritterDoesNotAssociateAnUnrelatedInventoryItem() {
        ForageMarkerLogic.PickupSession s = new ForageMarkerLogic.PickupSession();
        s.noteCritterSeen(77L, 3L, 100, 200,
            "gfx/kritter/dragonfly/dragonfly", 0);

        assertFalse(s.offerItem("loot", false, false, 45f,
            "gfx/invobjs/dragonfly-emerald", 10, 100));
    }

    @Test
    void snapshotNotCommittedIfMutatedOrShuttingDown() {
        assertTrue(ForageMarkerLogic.commitMarkSnapshot(3, 3, false));
        assertFalse(ForageMarkerLogic.commitMarkSnapshot(3, 4, false));
        assertFalse(ForageMarkerLogic.commitMarkSnapshot(3, 3, true));
        assertTrue(ForageMarkerLogic.rescheduleMarkSave(3, 4, false));
        assertFalse(ForageMarkerLogic.rescheduleMarkSave(3, 3, false));
        assertFalse(ForageMarkerLogic.rescheduleMarkSave(3, 4, true));
    }
}
