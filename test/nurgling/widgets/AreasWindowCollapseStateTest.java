package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreasWindowCollapseStateTest {
    @Test
    void compactWindowFollowsZoneListNotWiderPanels() {
        int listRight = 164;
        int specRight = 230;
        int tabWidth = 18;

        int compactWidth = AreasWindowCollapseState.compactWidth(listRight, tabWidth);

        assertEquals(182, compactWidth);
        assertTrue(compactWidth < specRight + tabWidth);
        assertEquals(164, AreasWindowCollapseState.alignedPanelWidth(164));
    }

    @Test
    void startsCompactAndTogglesBetweenCompactAndOriginalSize() {
        AreasWindowCollapseState state = new AreasWindowCollapseState(Coord.of(600, 500), 182, 164);

        assertFalse(state.expanded());
        assertFalse(state.detailsVisible());
        assertEquals(Coord.of(182, 500), state.size());
        assertEquals(164, state.searchWidth());
        assertEquals(AreasWindowCollapseState.Direction.RIGHT, state.direction());

        state.toggle();

        assertTrue(state.expanded());
        assertTrue(state.detailsVisible());
        assertEquals(Coord.of(600, 500), state.size());
        assertEquals(164, state.searchWidth());
        assertEquals(AreasWindowCollapseState.Direction.LEFT, state.direction());

        state.toggle();

        assertFalse(state.expanded());
        assertFalse(state.detailsVisible());
        assertEquals(Coord.of(182, 500), state.size());
    }

    @Test
    void openingAgainRestoresCompactState() {
        AreasWindowCollapseState state = new AreasWindowCollapseState(Coord.of(600, 500), 182, 164);
        state.toggle();

        state.collapse();

        assertFalse(state.expanded());
        assertFalse(state.detailsVisible());
        assertEquals(Coord.of(182, 500), state.size());
        assertEquals(164, state.searchWidth());
        assertEquals(AreasWindowCollapseState.Direction.RIGHT, state.direction());
    }
}
