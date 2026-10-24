package nurgling.widgets.cookbook;

import haven.Coord;
import haven.IButton;
import haven.Widget;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void variationsCloseControlHasClickableAreaAndInvokesCallback() {
        AtomicInteger closes = new AtomicInteger();
        VariationsPanel panel = new VariationsPanel(closes::incrementAndGet);
        IButton close = null;
        for (Widget child = panel.child; child != null; child = child.next) {
            if (child instanceof IButton) {
                close = (IButton) child;
                break;
            }
        }
        assertNotNull(close, "the variations panel needs a visible close control");
        assertTrue(close.checkhit(Coord.of(close.sz.x - 1, close.sz.y - 1)));
        close.click();
        assertEquals(1, closes.get());
    }
}
