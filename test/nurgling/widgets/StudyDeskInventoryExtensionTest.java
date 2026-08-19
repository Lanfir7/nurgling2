package nurgling.widgets;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyDeskInventoryExtensionTest {

    @Test
    void clampStockHoursStaysInRange() {
        assertEquals(6, StudyDeskInventoryExtension.clampStockHours(1));
        assertEquals(168, StudyDeskInventoryExtension.clampStockHours(999));
        assertEquals(24, StudyDeskInventoryExtension.clampStockHours(24));
    }

    @Test
    void formatTimeUsesCompactUnits() {
        assertEquals("0s", StudyDeskInventoryExtension.formatTime(0));
        assertEquals("6h", StudyDeskInventoryExtension.formatTime(6 * 3600));
        assertEquals("1d", StudyDeskInventoryExtension.formatTime(86400));
        assertEquals("1d 12h", StudyDeskInventoryExtension.formatTime(86400 + 12 * 3600));
        assertEquals("7d", StudyDeskInventoryExtension.formatTime(7 * 86400));
    }

    @Test
    void stockTimeColorUsesSliderBands() {
        int sixHours = 6 * 3600;
        assertEquals(StudyDeskInventoryExtension.STOCK_SHORT,
                StudyDeskInventoryExtension.stockTimeColor(sixHours - 1, 6));
        assertEquals(StudyDeskInventoryExtension.STOCK_OK,
                StudyDeskInventoryExtension.stockTimeColor(sixHours, 6));
        assertEquals(StudyDeskInventoryExtension.STOCK_OK,
                StudyDeskInventoryExtension.stockTimeColor(12 * 3600 - 1, 6));
        assertEquals(StudyDeskInventoryExtension.STOCK_LONG,
                StudyDeskInventoryExtension.stockTimeColor(12 * 3600, 6));
    }

    @Test
    void listSortsShortestRemainingFirst() {
        StudyDeskInventoryExtension.CurioInfo longStock =
                new StudyDeskInventoryExtension.CurioInfo("Long", 1_000_000);
        StudyDeskInventoryExtension.CurioInfo shortStock =
                new StudyDeskInventoryExtension.CurioInfo("Short", 1_000);
        StudyDeskInventoryExtension.CurioInfo midStock =
                new StudyDeskInventoryExtension.CurioInfo("Mid", 5_000);

        List<StudyDeskInventoryExtension.CurioInfo> visible =
                StudyDeskInventoryExtension.visibleSorted(
                        Arrays.asList(longStock, shortStock, midStock));

        assertEquals(3, visible.size());
        assertEquals("Short", visible.get(0).name);
        assertEquals("Mid", visible.get(1).name);
        assertEquals("Long", visible.get(2).name);
    }
}
