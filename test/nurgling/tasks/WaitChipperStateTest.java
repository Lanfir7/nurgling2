package nurgling.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static nurgling.tasks.WaitChipperState.State;

class WaitChipperStateTest {
    @Test
    void pilesWhenInventoryHasNoFreeSlots() {
        assertEquals(State.TIMEFORPILE, WaitChipperState.resolve(
                false, 0.8, 0.8, 0, false, false, false));
    }

    @Test
    void pilesWhenOnlyOneSlotLeft() {
        assertEquals(State.TIMEFORPILE, WaitChipperState.resolve(
                false, 0.8, 0.8, 1, false, false, false));
    }

    @Test
    void pilesWhenIdleEvenIfFreeSpaceUnknown() {
        assertEquals(State.TIMEFORPILE, WaitChipperState.resolve(
                false, 0.8, 0.8, -1, true, false, false));
    }

    @Test
    void pilesBeforeDrinkWhenInventoryIsFull() {
        assertEquals(State.TIMEFORPILE, WaitChipperState.resolve(
                false, 0.8, 0.40, 0, false, false, false));
    }

    @Test
    void keepsWorkingWhileChippingWithUnknownSpace() {
        assertEquals(State.WORKING, WaitChipperState.resolve(
                false, 0.8, 0.8, -1, false, false, false));
    }

    @Test
    void drinksWhenTiredAndInventoryHasRoom() {
        assertEquals(State.BUMLINGFORDRINK, WaitChipperState.resolve(
                false, 0.8, 0.40, 5, false, false, false));
    }

    @Test
    void ignoresFullInventoryWhenAsked() {
        assertEquals(State.WORKING, WaitChipperState.resolve(
                false, 0.8, 0.8, 0, true, false, true));
    }
}
