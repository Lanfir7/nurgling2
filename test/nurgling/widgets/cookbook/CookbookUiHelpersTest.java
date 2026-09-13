package nurgling.widgets.cookbook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookbookUiHelpersTest {
    @Test
    void spiceQualityFormatsWholeNumbersWithoutDecimal() {
        assertEquals("0", SpicePanel.quality(0));
        assertEquals("10", SpicePanel.quality(10));
        assertEquals("10.5", SpicePanel.quality(10.5));
    }

    @Test
    void recipeTableFormatsFixedDecimalsInRootLocale() {
        assertEquals("1.2", RecipeTable.fmt(1.23, 1));
        assertEquals("1.20", RecipeTable.fmt(1.2, 2));
        assertEquals("0.00", RecipeTable.fmt(0, 2));
    }
}
