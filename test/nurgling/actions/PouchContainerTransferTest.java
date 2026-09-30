package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PouchContainerTransferTest {
    @Test
    void explicitDropCountsOnlyAfterTheChestReceivesTheItem() throws Exception {
        Fake ops = new Fake();

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertTrue(outcome.handled);
        assertEquals(1, outcome.moved);
        assertFalse(outcome.handStuck);
        assertEquals(1, ops.takes);
        assertEquals(Collections.singletonList(PouchContainerTransfer.Place.FREE_SLOT), ops.places);
        assertEquals(0, ops.pouchRestores);
        assertEquals(0, ops.backpackRestores);
        assertEquals(Arrays.asList("take", "place", "receipt"), ops.events);
    }

    @Test
    void rejectionReturnsTheItemToThePouchAndDoesNotCountIt() throws Exception {
        Fake ops = new Fake();
        ops.received = false;
        ops.handAfterPlace = true;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertEquals(0, outcome.moved);
        assertFalse(outcome.handStuck);
        assertEquals(1, ops.pouchRestores);
        assertEquals(0, ops.backpackRestores);
    }

    @Test
    void noSpaceDoesNotTakeAndDoesNotCount() throws Exception {
        Fake ops = new Fake();
        ops.freeSpace = false;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 3);

        assertTrue(outcome.handled);
        assertEquals(0, outcome.moved);
        assertEquals(0, ops.takes);
        assertTrue(ops.places.isEmpty());
        assertFalse(outcome.handStuck);
    }

    @Test
    void fullMainInventoryDoesNotUseTheBackpackAndStopsWithTheHandOccupied() throws Exception {
        Fake ops = new Fake();
        ops.received = false;
        ops.handAfterPlace = true;
        ops.pouchRestore = false;
        ops.mainRoom = false;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertEquals(0, outcome.moved);
        assertTrue(outcome.handStuck);
        assertEquals(1, ops.pouchRestores);
        assertEquals(0, ops.backpackRestores);
    }

    @Test
    void stackChildMovesOneUnitOntoTheMatchingLooseItem() throws Exception {
        Fake ops = new Fake();
        ops.fromStack = true;
        ops.single = true;
        ops.notFullStack = true;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 8);

        assertEquals(1, outcome.moved);
        assertEquals(1, ops.takes);
        assertEquals(Collections.singletonList(PouchContainerTransfer.Place.SINGLE), ops.places);
    }

    @Test
    void backpackFallbackAfterAFailedPouchRestoreIsNotSuccess() throws Exception {
        Fake ops = new Fake();
        ops.received = false;
        ops.handAfterPlace = true;
        ops.pouchRestore = false;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertEquals(0, outcome.moved);
        assertFalse(outcome.handStuck);
        assertEquals(1, ops.backpackRestores);
    }

    @Test
    void backpackOnlyMoveIsNotSuccess() throws Exception {
        Fake ops = new Fake();
        ops.received = false;
        ops.handAfterPlace = false;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertEquals(0, outcome.moved);
        assertFalse(outcome.handStuck);
        assertEquals(0, ops.pouchRestores);
        assertEquals(0, ops.backpackRestores);
    }

    @Test
    void ordinaryInventoryItemKeepsTheExistingTransferPath() throws Exception {
        Fake ops = new Fake();
        ops.pouch = false;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 4);

        assertFalse(outcome.handled);
        assertEquals(0, outcome.moved);
        assertEquals(0, ops.takes);
        assertTrue(ops.places.isEmpty());
    }

    @Test
    void loosePouchItemFillsAPartialStackBeforeAFreeSlot() throws Exception {
        Fake ops = new Fake();
        ops.notFullStack = true;
        ops.single = true;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertEquals(Collections.singletonList(PouchContainerTransfer.Place.NOT_FULL_STACK), ops.places);
        assertEquals(1, outcome.moved);
    }

    @Test
    void zeroQuantityDoesNotTake() throws Exception {
        Fake ops = new Fake();

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 0);

        assertTrue(outcome.handled);
        assertEquals(0, outcome.moved);
        assertFalse(outcome.handStuck);
        assertEquals(0, ops.takes);
        assertTrue(ops.places.isEmpty());
        assertTrue(ops.events.isEmpty());
    }

    @Test
    void occupiedHandDoesNotTakeOrRestoreUnrelatedItem() throws Exception {
        Fake ops = new Fake();
        ops.occupied = true;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertTrue(outcome.handled);
        assertEquals(0, outcome.moved);
        assertTrue(outcome.handStuck);
        assertEquals(0, ops.takes);
        assertEquals(0, ops.pouchRestores);
        assertEquals(0, ops.backpackRestores);
        assertTrue(ops.places.isEmpty());
        assertTrue(ops.events.isEmpty());
    }

    @Test
    void failedTakeWithEmptyHandDoesNotCount() throws Exception {
        Fake ops = new Fake();
        ops.takeOk = false;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertTrue(outcome.handled);
        assertEquals(0, outcome.moved);
        assertFalse(outcome.handStuck);
        assertFalse(ops.occupied);
        assertEquals(1, ops.takes);
        assertTrue(ops.places.isEmpty());
        assertEquals(0, ops.pouchRestores);
        assertEquals(0, ops.backpackRestores);
        assertEquals(Collections.singletonList("take"), ops.events);
    }

    @Test
    void successfulDirectTransferWhileBackpackFull() throws Exception {
        Fake ops = new Fake();
        ops.mainRoom = false;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertTrue(outcome.handled);
        assertEquals(1, outcome.moved);
        assertFalse(outcome.handStuck);
        assertEquals(Collections.singletonList(PouchContainerTransfer.Place.FREE_SLOT), ops.places);
        assertEquals(0, ops.pouchRestores);
        assertEquals(0, ops.backpackRestores);
        assertEquals(Arrays.asList("take", "place", "receipt"), ops.events);
    }

    @Test
    void nonStackableTargetIgnoresMergeCandidates() throws Exception {
        Fake ops = new Fake();
        ops.stackable = false;
        ops.notFullStack = true;
        ops.single = true;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertEquals(1, outcome.moved);
        assertEquals(Collections.singletonList(PouchContainerTransfer.Place.FREE_SLOT), ops.places);
        assertEquals(Arrays.asList("take", "place", "receipt"), ops.events);
    }

    @Test
    void mergeIntoPartialStackWhenNoFreeSlot() throws Exception {
        Fake ops = new Fake();
        ops.freeSpace = false;
        ops.notFullStack = true;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertEquals(1, outcome.moved);
        assertEquals(1, ops.takes);
        assertEquals(Collections.singletonList(PouchContainerTransfer.Place.NOT_FULL_STACK), ops.places);
        assertEquals(Arrays.asList("take", "place", "receipt"), ops.events);
    }

    @Test
    void delayedFailingDestinationReceiptNeverCounted() throws Exception {
        Fake ops = new Fake();
        ops.received = false;
        ops.handAfterPlace = false;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertTrue(outcome.handled);
        assertEquals(0, outcome.moved);
        assertFalse(outcome.handStuck);
        assertEquals(1, ops.takes);
        assertEquals(Collections.singletonList(PouchContainerTransfer.Place.FREE_SLOT), ops.places);
        assertEquals(0, ops.pouchRestores);
        assertEquals(0, ops.backpackRestores);
        assertEquals(Arrays.asList("take", "place", "receipt"), ops.events);
    }

    @Test
    void transferDecisionRejectsFailedReceiptEvenAfterHandWasRestored() throws Exception {
        Fake ops = new Fake();
        ops.received = false;
        ops.handAfterPlace = true;

        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertFalse(outcome.handStuck);
        assertTrue(TransferToContainer.failOnNoProgress(outcome));
    }

    @Test
    void transferDecisionAllowsNoCapacitySoCallerCanTryNextChest() throws Exception {
        Fake ops = new Fake();
        ops.freeSpace = false;
        PouchContainerTransfer.Outcome outcome = PouchContainerTransfer.moveOne(ops, 1);

        assertFalse(outcome.failed);
        assertFalse(TransferToContainer.failOnNoProgress(outcome));
    }

    private static final class Fake implements PouchContainerTransfer.Ops {
        boolean pouch = true;
        boolean fromStack;
        boolean stackable = true;
        boolean notFullStack;
        boolean single;
        boolean freeSpace = true;
        boolean takeOk = true;
        boolean handAfterPlace;
        boolean received = true;
        boolean pouchRestore = true;
        boolean mainRoom = true;
        boolean occupied;
        int takes;
        int pouchRestores;
        int backpackRestores;
        final List<PouchContainerTransfer.Place> places = new ArrayList<PouchContainerTransfer.Place>();
        final List<String> events = new ArrayList<String>();

        @Override
        public boolean isPouchItem() {
            return pouch;
        }

        @Override
        public boolean fromStack() {
            return fromStack;
        }

        @Override
        public boolean stackable() {
            return stackable;
        }

        @Override
        public boolean handOccupied() {
            return occupied;
        }

        @Override
        public boolean hasNotFullStack() {
            return notFullStack;
        }

        @Override
        public boolean hasSingle() {
            return single;
        }

        @Override
        public boolean targetHasFreeSpace() {
            return freeSpace;
        }

        @Override
        public boolean takeToHand() {
            events.add("take");
            takes++;
            occupied = takeOk;
            return takeOk;
        }

        @Override
        public boolean place(PouchContainerTransfer.Place place) {
            events.add("place");
            places.add(place);
            occupied = handAfterPlace;
            return true;
        }

        @Override
        public boolean destinationReceived() {
            events.add("receipt");
            return received;
        }

        @Override
        public boolean restoreToSourcePouch() {
            pouchRestores++;
            if (pouchRestore)
                occupied = false;
            return pouchRestore;
        }

        @Override
        public boolean mainInventoryHasRoom() {
            return mainRoom;
        }

        @Override
        public boolean restoreToMainInventory() {
            backpackRestores++;
            if (!mainRoom)
                throw new AssertionError("backpack restore without room");
            occupied = false;
            return true;
        }
    }
}
