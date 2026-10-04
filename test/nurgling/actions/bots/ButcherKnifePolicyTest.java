package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButcherKnifePolicyTest {
    @Test
    void missingKnifeUsesTheBestToolWithoutInspect() {
        ButcherKnifePolicy.Loadout loadout = ButcherKnifePolicy.Loadout.of(null, 80.0);
        assertFalse(ButcherKnifePolicy.inspectEach(true, false, loadout));
        assertEquals(ButcherKnifePolicy.Choice.BEST, ButcherKnifePolicy.choose(true, false, loadout, 10.0));
    }

    @Test
    void knifeThatIsBestOrTiedSkipsInspect() {
        ButcherKnifePolicy.Loadout best = ButcherKnifePolicy.Loadout.of(90.0, 80.0);
        ButcherKnifePolicy.Loadout tied = ButcherKnifePolicy.Loadout.of(80.0, 80.0);
        ButcherKnifePolicy.Loadout only = ButcherKnifePolicy.Loadout.of(40.0, null);
        assertFalse(ButcherKnifePolicy.inspectEach(true, false, best));
        assertFalse(ButcherKnifePolicy.inspectEach(true, false, tied));
        assertFalse(ButcherKnifePolicy.inspectEach(true, false, only));
        assertEquals(ButcherKnifePolicy.Choice.KNIFE, ButcherKnifePolicy.choose(true, false, best, null));
        assertEquals(ButcherKnifePolicy.Choice.KNIFE, ButcherKnifePolicy.choose(true, false, tied, null));
        assertEquals(ButcherKnifePolicy.Choice.KNIFE, ButcherKnifePolicy.choose(true, false, only, null));
    }

    @Test
    void worseKnifeInspectsAndFollowsCarcassQuality() {
        ButcherKnifePolicy.Loadout loadout = ButcherKnifePolicy.Loadout.of(40.0, 80.0);
        assertTrue(ButcherKnifePolicy.inspectEach(true, false, loadout));
        assertEquals(ButcherKnifePolicy.Choice.KNIFE, ButcherKnifePolicy.choose(true, false, loadout, 40.0));
        assertEquals(ButcherKnifePolicy.Choice.KNIFE, ButcherKnifePolicy.choose(true, false, loadout, 39.9));
        assertEquals(ButcherKnifePolicy.Choice.BEST, ButcherKnifePolicy.choose(true, false, loadout, 40.1));
        assertEquals(ButcherKnifePolicy.Choice.BEST, ButcherKnifePolicy.choose(true, false, loadout, null));
    }

    @Test
    void disabledKnifeAlwaysUsesTheBestTool() {
        ButcherKnifePolicy.Loadout loadout = ButcherKnifePolicy.Loadout.of(40.0, 80.0);
        assertFalse(ButcherKnifePolicy.inspectEach(false, false, loadout));
        assertEquals(ButcherKnifePolicy.Choice.BEST, ButcherKnifePolicy.choose(false, false, loadout, 10.0));
    }

    @Test
    void permanentKnifeSkipsInspectEvenWhenWorse() {
        ButcherKnifePolicy.Loadout loadout = ButcherKnifePolicy.Loadout.of(40.0, 80.0);
        assertFalse(ButcherKnifePolicy.inspectEach(true, true, loadout));
        assertFalse(ButcherKnifePolicy.inspectEach(false, true, loadout));
        assertEquals(ButcherKnifePolicy.Choice.KNIFE, ButcherKnifePolicy.choose(true, true, loadout, 200.0));
        assertEquals(ButcherKnifePolicy.Choice.KNIFE, ButcherKnifePolicy.choose(false, true, loadout, 200.0));
    }

    @Test
    void cleaverNameIsTheFastButcherTool() {
        assertTrue(ButcherKnifePolicy.KNIFE.matches("Butcher's Cleaver"));
        assertTrue(ButcherKnifePolicy.KNIFE.matches("Butcher's cleaver"));
        assertFalse(ButcherKnifePolicy.KNIFE.matches("Stone Axe"));
    }

    @Test
    void settingsDefaultToKnifeOnAndPermanentOff() {
        assertTrue(ButcherKnifePolicy.useKnifeEnabled(null));
        assertTrue(ButcherKnifePolicy.useKnifeEnabled(true));
        assertFalse(ButcherKnifePolicy.useKnifeEnabled(false));
        assertFalse(ButcherKnifePolicy.alwaysKnifeEnabled(null));
        assertFalse(ButcherKnifePolicy.alwaysKnifeEnabled(false));
        assertTrue(ButcherKnifePolicy.alwaysKnifeEnabled(true));
    }
}
