package nurgling.widgets.craftatlas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasLayoutTest {
    @Test
    void wideUsesThreePanesAndNarrowUsesDetailsPage() {
        CraftAtlasLayout wide = CraftAtlasLayout.compute(1160, 700, 1.0);
        assertFalse(wide.detailsAsPage);
        assertTrue(wide.sidebar.w > 0 && wide.list.w > 0 && wide.details.w > 0);
        assertEquals(wide.header.y + wide.header.h, wide.sidebar.y);
        CraftAtlasLayout narrow = CraftAtlasLayout.compute(800, 600, 1.0);
        assertTrue(narrow.detailsAsPage);
        assertEquals(0, narrow.details.w);
    }

    @Test
    void scalingAndVisibleRangeKeepLastRowReachable() {
        CraftAtlasLayout scaled = CraftAtlasLayout.compute(1450, 875, 1.25);
        assertEquals(70, scaled.header.h);
        int[] range = CraftAtlasLayout.visibleRows(99999, 300, 30, 25);
        assertEquals(24, range[1]);
        assertTrue(range[0] <= range[1]);
    }

    @Test
    void detailsLeaveAVisibleFooterForCraftActions() {
        CraftAtlasLayout layout = CraftAtlasLayout.compute(1160, 700, 1.0);
        assertTrue(layout.details.y + layout.details.h <= 644,
                "details content must stop above the fixed craft action bar");
    }

    @Test
    void metricTableExpandsTheRecipePane() {
        CraftAtlasLayout regular = CraftAtlasLayout.compute(1160, 700, 1.0);
        CraftAtlasLayout table = CraftAtlasLayout.compute(1160, 700, 1.0, true);

        assertTrue(table.list.w > regular.list.w);
        assertTrue(table.details.w >= 320);
    }

    @Test
    void requestedRecipePaneWidthIsUsedAndClamped() {
        CraftAtlasLayout requested = CraftAtlasLayout.compute(1160, 700, 1.0, true, 480);
        CraftAtlasLayout tooWide = CraftAtlasLayout.compute(1160, 700, 1.0, true, 5000);
        CraftAtlasLayout tooNarrow = CraftAtlasLayout.compute(1160, 700, 1.0, true, 20);

        assertEquals(480, requested.list.w);
        assertEquals(480, requested.details.w);
        assertEquals(280, tooNarrow.list.w);
        assertEquals(320, tooWide.details.w);
    }
}
