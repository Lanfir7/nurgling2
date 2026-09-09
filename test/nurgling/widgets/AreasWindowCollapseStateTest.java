package nurgling.widgets;

import haven.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreasWindowCollapseStateTest {
    @Test
    void startsCompactAndTogglesBetweenCompactAndOriginalSize() {
        AreasWindowCollapseState state = new AreasWindowCollapseState(Coord.of(600, 500), 180);

        assertFalse(state.expanded());
        assertFalse(state.detailsVisible());
        assertEquals(Coord.of(180, 500), state.size());
        assertEquals(180, state.searchWidth());
        assertEquals(AreasWindowCollapseState.Direction.RIGHT, state.direction());

        state.toggle();

        assertTrue(state.expanded());
        assertTrue(state.detailsVisible());
        assertEquals(Coord.of(600, 500), state.size());
        assertEquals(180, state.searchWidth());
        assertEquals(AreasWindowCollapseState.Direction.LEFT, state.direction());

        state.toggle();

        assertFalse(state.expanded());
        assertFalse(state.detailsVisible());
        assertEquals(Coord.of(180, 500), state.size());
    }

    @Test
    void openingAgainRestoresCompactState() {
        AreasWindowCollapseState state = new AreasWindowCollapseState(Coord.of(600, 500), 180);
        state.toggle();

        state.collapse();

        assertFalse(state.expanded());
        assertFalse(state.detailsVisible());
        assertEquals(Coord.of(180, 500), state.size());
        assertEquals(AreasWindowCollapseState.Direction.RIGHT, state.direction());
    }
}
