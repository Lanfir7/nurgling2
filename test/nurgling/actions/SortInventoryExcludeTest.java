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

    @Test
    void russianCharacterSheetIsExcluded() {
        String prev = nurgling.i18n.L10n.getLanguage();
        try {
            nurgling.i18n.L10n.setLanguage("ru");
            assertTrue(SortInventory.isExcludedWindow(nurgling.i18n.L10n.get("char.window_title")));
        } finally {
            nurgling.i18n.L10n.setLanguage(prev);
        }
    }
}
