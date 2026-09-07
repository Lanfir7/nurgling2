package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DTargetExplicitModifiersTest {
    @Test void interactCarriesCanonicalModifiersAcrossDispatch() {
        DTarget.Interact event = new DTarget.Interact(Coord.z, null,
                UI.MOD_CTRL | UI.MOD_META);
        assertEquals(UI.MOD_CTRL | UI.MOD_META, event.mods);
        DTarget.Interact derived = event.derive(Coord.of(4, 5));
        assertEquals(event.mods, derived.mods);
    }
}
