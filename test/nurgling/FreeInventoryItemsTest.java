package nurgling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FreeInventoryItemsTest {

    @AfterEach
    void resetBeltPouches() {
        FreeInventoryItems.includeBeltPouches(false);
    }

    @Test
    void backpackItemsStayAloneUntilBeltPouchesAreIncluded() {
        Object backpack = new Object();
        Object pouchItem = new Object();
        ArrayList<Object> items = new ArrayList<Object>(Arrays.asList(backpack));

        ArrayList<Object> closed = FreeInventoryItems.withBeltPouches(
                items, Arrays.asList(pouchItem));

        assertEquals(Arrays.asList(backpack), closed);

        FreeInventoryItems.includeBeltPouches(true);
        ArrayList<Object> open = FreeInventoryItems.withBeltPouches(
                new ArrayList<Object>(Arrays.asList(backpack)),
                Arrays.asList(pouchItem));

        assertEquals(Arrays.asList(backpack, pouchItem), open);
    }

    @Test
    void itemAlreadyInTheBackpackIsNotListedTwice() {
        Object shared = new Object();
        FreeInventoryItems.includeBeltPouches(true);

        ArrayList<Object> merged = FreeInventoryItems.withBeltPouches(
                new ArrayList<Object>(Arrays.asList(shared)),
                Arrays.asList(shared));

        assertEquals(1, merged.size());
        assertSame(shared, merged.get(0));
    }

    @Test
    void emptyPouchContentsLeaveTheBackpackListUntouched() {
        Object backpack = new Object();
        ArrayList<Object> items = new ArrayList<Object>(Arrays.asList(backpack));
        FreeInventoryItems.includeBeltPouches(true);

        ArrayList<Object> merged = FreeInventoryItems.withBeltPouches(
                items, Arrays.<Object>asList());

        assertSame(items, merged);
        assertEquals(Arrays.asList(backpack), merged);
    }
}
