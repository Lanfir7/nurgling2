package nurgling.actions;

import haven.ItemInfo;
import haven.Loading;
import nurgling.NGItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSaveTablewareTest {
    @Test
    void defersItemUntilItsInfoIsLoaded() {
        assertFalse(AutoSaveTableware.shouldRemove(new LoadingItem()));
    }

    @Test
    void removesWhenOneWearLeft() {
        assertTrue(AutoSaveTableware.shouldRemove(19, 20));
        assertTrue(AutoSaveTableware.shouldRemove(20, 20));
    }

    @Test
    void keepsHealthyTableware() {
        assertFalse(AutoSaveTableware.shouldRemove(0, 20));
        assertFalse(AutoSaveTableware.shouldRemove(18, 20));
        assertFalse(AutoSaveTableware.shouldRemove(0, 0));
    }

    @Test
    void takesOffToInventoryWhenThereIsSpace() {
        assertEquals(AutoSaveTableware.TakeOff.TO_INVENTORY, AutoSaveTableware.takeOffMode(1));
        assertEquals(AutoSaveTableware.TakeOff.DROP, AutoSaveTableware.takeOffMode(0));
    }

    @Test
    void watchesFeastTableWithoutSaltGrid() {
        assertTrue(AutoSaveTableware.canWatch(true, true));
        assertFalse(AutoSaveTableware.canWatch(true, false));
        assertFalse(AutoSaveTableware.canWatch(false, true));
    }

    private static class LoadingItem extends NGItem {
        LoadingItem() {
            super(() -> null);
        }

        @Override
        public List<ItemInfo> info() {
            throw new Loading("Still waiting for sprite to be constructed");
        }
    }
}
