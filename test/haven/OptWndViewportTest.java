package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptWndViewportTest {
    @Test
    void shrinksViewportByTheAmountWindowWouldOverflowTheClient() {
        assertEquals(new Coord(808, 538), SettingsViewportLayout.fit(
                new Coord(808, 600),
                new Coord(842, 642),
                new Coord(900, 600),
                10,
                new Coord(240, 160)));
    }

    @Test
    void keepsNaturalViewportWhenWindowAlreadyFits() {
        assertEquals(new Coord(808, 600), SettingsViewportLayout.fit(
                new Coord(808, 600),
                new Coord(842, 642),
                new Coord(1920, 1080),
                10,
                new Coord(240, 160)));
    }

    @Test
    void preservesUsableMinimumOnVerySmallClients() {
        assertEquals(new Coord(240, 160), SettingsViewportLayout.fit(
                new Coord(808, 600),
                new Coord(842, 642),
                new Coord(200, 120),
                10,
                new Coord(240, 160)));
    }

    @Test
    void usesOnlyTheVisiblePartOfTheParentAtTheLoginScreen() {
        Area visible = SettingsViewportLayout.visibleArea(
                new Coord(656, 653),
                Area.sized(new Coord(0, 77), new Coord(656, 650)));

        assertEquals(new Coord(0, 77), visible.ul);
        assertEquals(new Coord(656, 653), visible.br);
    }

    @Test
    void keepsTheWholeRootWhenTheParentAreaIsUnavailable() {
        Area visible = SettingsViewportLayout.visibleArea(new Coord(656, 653), null);

        assertEquals(Coord.z, visible.ul);
        assertEquals(new Coord(656, 653), visible.br);
    }

    @Test
    void detachedWindowIsNotEligibleForViewportLayout() {
        Widget panel = new Widget();
        Coord rootSize = new Coord(656, 653);

        assertFalse(SettingsViewportLayout.canFit(panel, null, rootSize));
        assertTrue(SettingsViewportLayout.canFit(panel, new Widget(), rootSize));
    }
}
