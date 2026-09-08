package nurgling;

import haven.Button;
import haven.UI;
import haven.Widget;
import nurgling.i18n.L10n;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NFightWndLayoutTest {
    @Test
    void combatCategoryButtonsAreCenteredAboveActionList() {
        NFightWnd window = new NFightWnd(5, 10, 30);
        List<Widget> categories = window.children().stream()
                .filter(child -> child.getClass().getSimpleName().equals("CategoryButton"))
                .collect(Collectors.toList());

        assertEquals(6, categories.size());
        int categoryLeft = categories.stream().mapToInt(child -> child.c.x).min().orElseThrow(AssertionError::new);
        int categoryRight = categories.stream().mapToInt(child -> child.c.x + child.sz.x).max().orElseThrow(AssertionError::new);
        int categoryCenterTwice = categoryLeft + categoryRight;
        int actionListCenterTwice = window.actlist.c.x * 2 + window.actlist.sz.x;

        assertTrue(Math.abs(categoryCenterTwice - actionListCenterTwice) <= 1,
                "Combat category buttons must be centered over the action list");
    }

    @Test
    void schoolControlsFitWithinSaveSlotRow() {
        NFightWnd window = new NFightWnd(5, 10, 30);
        Button load = button(window, L10n.get("char.fight.load"));
        Button save = button(window, L10n.get("char.fight.save"));
        Button rename = button(window, L10n.get("char.fight.rename"));

        assertEquals(load.c.y, save.c.y, "Load and Save must share one row");
        assertEquals(load.c.x + load.sz.x + UI.scale(3), save.c.x,
                "Load and Save must not overlap");

        int saveSlotsBottom = window.children().stream()
                .filter(child -> child.sz.equals(UI.scale(77, 60)))
                .mapToInt(child -> child.c.y + child.sz.y)
                .max()
                .orElseThrow(() -> new AssertionError("Save slots not found"));
        assertTrue(rename.c.y + rename.sz.y <= saveSlotsBottom,
                "Controls must not make the combat-school window taller");
    }

    private static Button button(NFightWnd window, String text) {
        for(Widget child : window.children()) {
            if(child instanceof Button && ((Button)child).text.text.equals(text))
                return (Button)child;
        }
        throw new AssertionError("Button not found: " + text);
    }
}
