package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightObjectCrucibleFuelTest {
    private static final String CRUCIBLE = "gfx/terobjs/crucible";
    private static final int BRANCHES = 1;
    private static final int COAL = 2;
    private static final int FIRE = 4;

    @Test
    void crucibleFuelMaskAcceptsBranchesOrCoalAndKeepsFireBit() {
        LightObject.LightConfig config = LightObject.getConfig(CRUCIBLE);
        assertNotNull(config);
        assertEquals(FIRE, config.fireFlag);

        assertTrue(hasFuel(BRANCHES, config), "branches (marker 1) count as fuel");
        assertTrue(hasFuel(COAL, config), "coal (marker 2) counts as fuel");
        assertTrue(hasFuel(BRANCHES | COAL, config), "both fuels count as fuel");
        assertTrue(hasFuel(BRANCHES | FIRE, config), "lit + branches still counts as fueled");
        assertFalse(hasFuel(0, config), "empty crucible has no fuel");
        assertFalse(hasFuel(FIRE, config), "lit-only is not fuel");
    }

    /** Same empty-check LightObject uses before acquiring an implement. */
    private static boolean hasFuel(int attr, LightObject.LightConfig config) {
        return config.fuelFlag != 0 && (attr & config.fuelFlag) != 0;
    }
}
