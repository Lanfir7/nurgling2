package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SortInventoryExcludeTest {

    @Test
    void studyDeskIsSortableUnlikeStudyWindow() {
        assertTrue(SortInventory.isExcludedWindow("Study"));
        assertFalse(SortInventory.isExcludedWindow("Study Desk"));
        assertFalse(SortInventory.isExcludedWindow("Fine Study Desk"));
        assertFalse(SortInventory.isExcludedWindow("Grand Study Desk"));
    }

    @Test
    void otherExcludedWindowsStayExcluded() {
        assertTrue(SortInventory.isExcludedWindow("Character Sheet"));
        assertTrue(SortInventory.isExcludedWindow("Herbalist Table"));
        assertFalse(SortInventory.isExcludedWindow("Chest"));
        assertFalse(SortInventory.isExcludedWindow(null));
    }
}
