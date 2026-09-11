package nurgling.actions.bots.forager;

import haven.Coord2d;
import haven.MCache;
import nurgling.routes.ForagerPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerRouteConstraintsTest {

    @Test
    void withinLeashHonorsTileCapAndUnlimited() {
        ForagerPath unlimited = new ForagerPath("open");
        ForagerRouteConstraints open = new ForagerRouteConstraints(unlimited);
        Coord2d origin = Coord2d.of(0, 0);
        Coord2d far = Coord2d.of(MCache.tilesz.x * 200, 0);
        assertTrue(open.withinLeash(origin, far));

        ForagerPath capped = new ForagerPath("capped");
        capped.maxDistance = 10;
        ForagerRouteConstraints leash = new ForagerRouteConstraints(capped);
        assertTrue(leash.withinLeash(origin, Coord2d.of(MCache.tilesz.x * 10, 0)));
        assertFalse(leash.withinLeash(origin, Coord2d.of(MCache.tilesz.x * 11, 0)));
    }

    @Test
    void exclusionTilesAreRememberedPerSegment() {
        ForagerPath path = new ForagerPath("paint");
        path.paintExclusion(7L, new haven.Coord(3, 4));
        assertTrue(path.isExcluded(7L, new haven.Coord(3, 4)));
        assertFalse(path.isExcluded(7L, new haven.Coord(4, 4)));
        assertFalse(path.isExcluded(8L, new haven.Coord(3, 4)));
    }

    @Test
    void detourBudgetStopsAfterConfiguredHops() {
        DetourBranchBudget budget = new DetourBranchBudget(1, 5);
        assertTrue(budget.canBranch());
        budget.spend(MCache.tilesz.x);
        assertFalse(budget.canBranch());
    }
}
