package nurgling.widgets.bots;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterMinerGroundStacksTest {

    @Test
    void pickupRadiusMatchesVisiblePathfinderArea() {
        assertEquals(41 * 11, MasterMinerGroundStacks.PICKUP_RADIUS, 0.001);
    }

    @Test
    void onlyLooseGroundItemsCount() {
        assertTrue(MasterMinerGroundStacks.isGroundItem("gfx/terobjs/items/dolomite"));
        assertTrue(MasterMinerGroundStacks.isGroundItem("gfx/terobjs/items/gems/gemstone"));
        assertFalse(MasterMinerGroundStacks.isGroundItem("gfx/terobjs/items/decal-foo"));
        assertFalse(MasterMinerGroundStacks.isGroundItem("gfx/terobjs/items/parchment-decal"));
        assertFalse(MasterMinerGroundStacks.isGroundItem("gfx/terobjs/plants/garlic"));
        assertFalse(MasterMinerGroundStacks.isGroundItem("gfx/terobjs/stockpile-stone"));
        assertFalse(MasterMinerGroundStacks.isGroundItem(null));
    }

    @Test
    void groupsByResourceAndIgnoresOutOfRadius() {
        List<MasterMinerGroundStacks.Drop> drops = Arrays.asList(
                drop("gfx/terobjs/items/dolomite", 0, 0),
                drop("gfx/terobjs/items/dolomite", 20, 0),
                drop("gfx/terobjs/items/microlite", 5, 0),
                drop("gfx/terobjs/items/dolomite", 5000, 0)
        );
        List<MasterMinerGroundStacks.Stack> stacks = MasterMinerGroundStacks.group(drops, 0, 0, 451);
        assertEquals(2, stacks.size());
        assertEquals("gfx/terobjs/items/dolomite", stacks.get(0).resPath);
        assertEquals(2, stacks.get(0).count);
        assertEquals("Dolomite", stacks.get(0).displayName);
        assertEquals("gfx/terobjs/items/microlite", stacks.get(1).resPath);
        assertEquals(1, stacks.get(1).count);
    }

    @Test
    void displayNameAndInventoryIconPathComeFromResourceSlug() {
        assertEquals("Dolomite", MasterMinerGroundStacks.displayName("gfx/terobjs/items/dolomite"));
        assertEquals("gfx/invobjs/dolomite", MasterMinerGroundStacks.iconInvPath("gfx/terobjs/items/dolomite"));
        assertEquals("Gemstone", MasterMinerGroundStacks.displayName("gfx/terobjs/items/gems/gemstone"));
        assertEquals("gfx/invobjs/gemstone", MasterMinerGroundStacks.iconInvPath("gfx/terobjs/items/gems/gemstone"));
    }

    @Test
    void clickPicksThirtyAndShiftPicksAll() {
        assertEquals(30, MasterMinerGroundStacks.CLICK_PICKUP_LIMIT);
        assertEquals(30, MasterMinerGroundStacks.pickupCap(false));
        assertEquals(Integer.MAX_VALUE, MasterMinerGroundStacks.pickupCap(true));
    }

    private static MasterMinerGroundStacks.Drop drop(String path, double x, double y) {
        return new MasterMinerGroundStacks.Drop(path, x, y);
    }
}
