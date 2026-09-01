package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiftableCatalogTest {

    @Test
    void treeLogsAreLiftable() {
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/trees/oaklog"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/trees/birchlog"));
        assertTrue(PrepQuota.isLog("gfx/terobjs/trees/oaklog"));
    }

    @Test
    void commonStructuresAreLiftableWhenMapped() {
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/barrel"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/crate"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/cupboard"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/trough"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/ttub"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/cheeserack"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/htable"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/gardenpot"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/beehive"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/anvil"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/quern"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/loom"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/swheel"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/vehicle/wheelbarrow"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/vehicle/plow"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/chest"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/furn/bed-sturdy"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/furn/table-stone"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/furn/table-oak"));
    }

    @Test
    void bushesUseClientBushPrefixNotCropPlants() {
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/bushes/arrowwood"));
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/bushes/teabush"));
        assertFalse(LiftableCatalog.isLiftable("gfx/terobjs/plants/pumpkin"));
        assertFalse(LiftableCatalog.isLiftable("gfx/terobjs/plants/trellis"));
    }

    @Test
    void bouldersMatchBumlingPrefix() {
        assertTrue(LiftableCatalog.isLiftable("gfx/terobjs/bumlings/granite"));
    }

    @Test
    void nonLiftablesAreFalse() {
        assertFalse(LiftableCatalog.isLiftable("gfx/terobjs/trees/oakstump"));
        assertFalse(LiftableCatalog.isLiftable("gfx/terobjs/trees/oak"));
        assertFalse(LiftableCatalog.isLiftable("gfx/terobjs/kiln"));
        assertFalse(LiftableCatalog.isLiftable("gfx/borka/body"));
        assertFalse(LiftableCatalog.isLiftable("gfx/terobjs/arch/logcabin"));
        assertFalse(LiftableCatalog.isLiftable(null));
        assertFalse(LiftableCatalog.isLiftable(""));
    }

    @Test
    void objectFilterIsExactClickedResource() {
        NAlias oak = LiftableCatalog.objectFilter("gfx/terobjs/trees/oaklog");
        assertTrue(oak.matches("gfx/terobjs/trees/oaklog"));
        assertFalse(oak.matches("gfx/terobjs/trees/birchlog"));
        assertFalse(oak.matches("gfx/terobjs/barrel"));

        NAlias barrel = LiftableCatalog.objectFilter("gfx/terobjs/barrel");
        assertTrue(barrel.matches("gfx/terobjs/barrel"));
        assertFalse(barrel.matches("gfx/terobjs/crate"));
    }
}
