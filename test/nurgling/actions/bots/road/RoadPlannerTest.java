package nurgling.actions.bots.road;

import haven.Coord;
import haven.Coord2d;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPlannerTest {
    private static final double TILE = 11;
    private static final List<Coord2d> ROUTE = Arrays.asList(
            Coord2d.of(0, 0), Coord2d.of(500, 0));

    @Test
    void clearGroundTakesTheLongestStepOnTheRoute() {
        RoadPlanner.Candidate c = planner(open()).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(Math.abs(c.pos.y) > 2 * TILE, "y=" + c.pos.y);
        assertTrue(Math.abs(c.pos.y) < 4 * TILE, "y=" + c.pos.y);
        assertTrue(c.pos.x > 15 * TILE, "x=" + c.pos.x);
        Coord2d road = RoadGeometry.anchor(c.pos, c.angle, TILE);
        assertTrue(Math.abs(road.y) < Math.abs(c.pos.y), "roadY=" + road.y);
    }

    @Test
    void stoneMilestoneReachesThirtyTiles() {
        RoadPlanner.Candidate c = new RoadPlanner(ROUTE, TILE, open(), RoadRules.maxSegment(true))
                .next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.step == 30, "step=" + c.step);
    }

    @Test
    void obstacleOnTheAxisShiftsSideways() {
        RoadTerrain offAxis = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return Math.abs(pos.y - TILE / 2.0) > 1; }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
        };
        RoadPlanner.Candidate c = planner(offAxis).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(Math.abs(c.pos.y - TILE / 2.0) > 1, "y=" + c.pos.y);
    }

    @Test
    void standingMilestoneKeepsFiveTilesBetween() {
        RoadPlanner.Candidate blocked = planner(open()).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(blocked);
        final Coord2d occupied = blocked.pos;
        RoadTerrain crowded = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return true; }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
            public boolean clearOfMilestones(Coord2d pos) {
                return !RoadRules.tooClose(pos.dist(occupied), TILE);
            }
        };
        RoadPlanner.Candidate c = planner(crowded).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.pos.dist(occupied) >= (RoadRules.TILES_BETWEEN + 1) * TILE - 1, "dist=" + c.pos.dist(occupied));
    }

    @Test
    void blockedNearSideUsesTheOtherSide() {
        RoadTerrain blockedAhead = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return pos.y < 0; }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
        };
        RoadPlanner.Candidate c = planner(blockedAhead).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.pos.y < 0, "y=" + c.pos.y);
    }

    @Test
    void blockedRoadFromThePreviousStoneSkipsTheSpot() {
        RoadTerrain wall = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return true; }
            public boolean lineClear(Coord2d from, Coord2d to) { return to.x < 18 * TILE; }
            public boolean routePointClear(Coord2d point) { return true; }
        };
        RoadPlanner.Candidate c = planner(wall).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(RoadGeometry.anchor(c.pos, c.angle, TILE).x < 18 * TILE, "pos=" + c.pos);
    }

    @Test
    void muchLongerStepWithABushOnTheLineBeatsAShortClearOne() {
        RoadTerrain bush = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return true; }
            public boolean lineClear(Coord2d from, Coord2d to) { return to.x < 9 * TILE; }
            public boolean routePointClear(Coord2d point) { return true; }
        };
        RoadPlanner.Candidate c = planner(bush).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.step >= 18, "step=" + c.step);
    }

    @Test
    void wallAtTheLongStepShortensTheStep() {
        RoadTerrain near = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return pos.x < 19 * TILE; }
            public boolean lineClear(Coord2d from, Coord2d to) { return to.x < 19 * TILE; }
        };
        RoadPlanner.Candidate c = planner(near).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.pos.x < 19 * TILE, "x=" + c.pos.x);
        assertTrue(c.pos.x > 10 * TILE, "x=" + c.pos.x);
    }

    @Test
    void rejectedOnlyTileYieldsNothing() {
        RoadPlanner open = planner(open());
        RoadPlanner.Candidate first = open.next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(first);
        final Coord only = open.tileOf(first.pos);
        Set<Coord> rejected = new HashSet<Coord>();
        rejected.add(only);
        RoadTerrain justThat = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return open.tileOf(pos).equals(only); }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
        };
        assertNull(planner(justThat).next(Coord2d.of(0, 0), 0, rejected));
    }

    @Test
    void sharpTurnShortensTheStepSoTheCornerIsNotCutTooFar() {
        List<Coord2d> elbow = Arrays.asList(
                Coord2d.of(0, 0), Coord2d.of(10 * TILE, 0), Coord2d.of(10 * TILE, 300));
        RoadPlanner.Candidate c = new RoadPlanner(elbow, TILE, open())
                .next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.step > 10, "step=" + c.step);
        assertTrue(c.step < 20, "step=" + c.step);
    }

    @Test
    void spotTheCharacterCannotWalkToIsSkipped() {
        RoadTerrain wall = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return true; }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
            public boolean walkClear(Coord2d from, Coord2d to) { return to.x < 14 * TILE; }
        };
        RoadPlanner.Candidate c = planner(wall).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.pos.x < 14 * TILE, "pos=" + c.pos);
    }

    @Test
    void cliffBetweenStonesIsNeverCrossed() {
        RoadTerrain cliff = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return true; }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
            public boolean cliffBetween(Coord2d from, Coord2d to) {
                return Math.max(from.x, to.x) > 12 * TILE;
            }
        };
        RoadPlanner.Candidate c = planner(cliff).next(Coord2d.of(0, 0), 0, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.pos.x <= 12 * TILE, "pos=" + c.pos);
    }

    @Test
    void finishedNearTheEndOfTheRoute() {
        RoadPlanner planner = planner(open());
        assertTrue(planner.finished(Coord2d.of(490, 0)));
        assertTrue(!planner.finished(Coord2d.of(0, 0)));
    }

    private static RoadPlanner planner(RoadTerrain terrain) {
        return new RoadPlanner(ROUTE, TILE, terrain);
    }

    @Test
    void narrowPassageKeepsTheStoneOffTheRoad() {
        RoadTerrain corridor = new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return Math.abs(pos.y) < 2.5 * TILE; }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
            public boolean routePointClear(Coord2d point) { return Math.abs(point.y) < 2.5 * TILE; }
        };
        RoadPlanner.Candidate c = planner(corridor).next(Coord2d.of(0, 0), Math.PI, new HashSet<Coord>());
        assertNotNull(c);
        assertTrue(c.step >= 18, "step=" + c.step);
        assertTrue(Math.abs(c.pos.y) >= 1.5 * TILE, "the stone stands on the road: " + c.pos);
        assertTrue(Math.cos(c.angle) > -0.3, "the route goes on behind the stone: " + c.angle);
    }

    @Test
    void mineCornerGetsAStoneWhoseRoadStartsOnTheCorner() {
        final double cx = 159.5, cy = 5.5;
        RoadTerrain mine = new RoadTerrain() {
            boolean floor(Coord2d p) {
                return (Math.abs(p.y - cy) < 1.5 * TILE && p.x < cx + 1.5 * TILE)
                        || (Math.abs(p.x - cx) < 1.5 * TILE && p.y > cy - 1.5 * TILE);
            }
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return floor(pos); }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
            public boolean routePointClear(Coord2d point) { return floor(point); }
            public boolean cliffBetween(Coord2d from, Coord2d to) {
                for (int i = 0; i <= 50; i++)
                    if (!floor(from.add(to.sub(from).mul(i / 50.0)))) return true;
                return false;
            }
        };
        List<Coord2d> elbow = Arrays.asList(Coord2d.of(0, cy), Coord2d.of(cx, cy), Coord2d.of(cx, 400));
        RoadPlanner.Candidate c = new RoadPlanner(elbow, TILE, mine)
                .next(Coord2d.of(38.5, cy), Math.PI, new HashSet<Coord>());
        assertNotNull(c);
        Coord2d road = RoadGeometry.anchor(c.pos, c.angle, TILE);
        assertTrue(road.dist(Coord2d.of(cx, cy)) <= 1.5 * TILE, "road starts at " + road + ", stone " + c.pos);
    }

    private static RoadTerrain open() {
        return new RoadTerrain() {
            public boolean loaded(Coord2d p) { return true; }
            public boolean siteOk(Coord2d pos, double angle) { return true; }
            public boolean lineClear(Coord2d from, Coord2d to) { return true; }
        };
    }
}
