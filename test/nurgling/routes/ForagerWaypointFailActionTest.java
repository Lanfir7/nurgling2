package nurgling.routes;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerWaypointFailActionTest {

    @Test
    void normalizeCanonicalIds() {
        assertEquals("nothing", ForagerWaypoint.normalizeOnStepsFailAction(null));
        assertEquals("nothing", ForagerWaypoint.normalizeOnStepsFailAction(""));
        assertEquals("nothing", ForagerWaypoint.normalizeOnStepsFailAction("nothing"));
        assertEquals("break", ForagerWaypoint.normalizeOnStepsFailAction("break"));
        assertEquals("logout", ForagerWaypoint.normalizeOnStepsFailAction("logout"));
        assertEquals("travel hearth", ForagerWaypoint.normalizeOnStepsFailAction("travel hearth"));
    }

    @Test
    void normalizeTravelHearthAliases() {
        assertEquals("travel hearth", ForagerWaypoint.normalizeOnStepsFailAction("travel-hearth"));
        assertEquals("travel hearth", ForagerWaypoint.normalizeOnStepsFailAction("travel_hearth"));
        assertEquals("travel hearth", ForagerWaypoint.normalizeOnStepsFailAction("TRAVEL HEARTH"));
    }

    @Test
    void nothingContinuesRouteBreakStopsWithoutOutcome() {
        assertFalse(ForagerWaypoint.stopsRouteOnStepsFail("nothing"));
        assertTrue(ForagerWaypoint.stopsRouteOnStepsFail("break"));
        assertTrue(ForagerWaypoint.stopsRouteOnStepsFail("logout"));
        assertTrue(ForagerWaypoint.stopsRouteOnStepsFail("travel-hearth"));
        assertFalse(ForagerWaypoint.performsGuardOutcomeOnStepsFail("nothing"));
        assertFalse(ForagerWaypoint.performsGuardOutcomeOnStepsFail("break"));
        assertTrue(ForagerWaypoint.performsGuardOutcomeOnStepsFail("logout"));
        assertTrue(ForagerWaypoint.performsGuardOutcomeOnStepsFail("travel hearth"));
    }

    @Test
    void jsonRoundTripKeepsBreakDistinctFromOmittedNothing() {
        ForagerWaypoint nothingWp = new ForagerWaypoint(1L, new haven.Coord(0, 0));
        nothingWp.onStepsFailAction = "nothing";
        JSONObject nothingJson = nothingWp.toJson();
        assertFalse(nothingJson.has("onStepsFailAction"));
        ForagerWaypoint loadedNothing = new ForagerWaypoint(nothingJson);
        assertEquals("nothing", loadedNothing.onStepsFailAction);

        ForagerWaypoint breakWp = new ForagerWaypoint(1L, new haven.Coord(0, 0));
        breakWp.onStepsFailAction = "break";
        JSONObject breakJson = breakWp.toJson();
        assertEquals("break", breakJson.getString("onStepsFailAction"));
        ForagerWaypoint loadedBreak = new ForagerWaypoint(breakJson);
        assertEquals("break", loadedBreak.onStepsFailAction);
        assertTrue(ForagerWaypoint.stopsRouteOnStepsFail(loadedBreak.onStepsFailAction));
        assertFalse(ForagerWaypoint.stopsRouteOnStepsFail(loadedNothing.onStepsFailAction));
    }

    @Test
    void jsonLoadsHyphenatedTravelHearth() {
        JSONObject json = new ForagerWaypoint(1L, new haven.Coord(2, 3)).toJson();
        json.put("onStepsFailAction", "travel-hearth");
        ForagerWaypoint loaded = new ForagerWaypoint(json);
        assertEquals("travel hearth", loaded.onStepsFailAction);
        assertTrue(ForagerWaypoint.performsGuardOutcomeOnStepsFail(loaded.onStepsFailAction));
    }
}
