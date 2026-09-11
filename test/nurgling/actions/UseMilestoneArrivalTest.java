package nurgling.actions;

import haven.Coord2d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UseMilestoneArrivalTest {

    private static final double TOLERANCE = 100;

    @Test
    void unresolvedDestWorldCoordIsNotAWrongPlace() {
        assertEquals(UseMilestone.Arrival.UNRESOLVED,
                UseMilestone.classifyArrival(null, new Coord2d(10, 10), TOLERANCE));
    }

    @Test
    void missingPlayerIsNotAWrongPlace() {
        assertEquals(UseMilestone.Arrival.UNRESOLVED,
                UseMilestone.classifyArrival(new Coord2d(10, 10), null, TOLERANCE));
    }

    @Test
    void farLandingIsConfirmedWrongPlace() {
        assertEquals(UseMilestone.Arrival.WRONG_PLACE,
                UseMilestone.classifyArrival(new Coord2d(0, 0), new Coord2d(1000, 0), TOLERANCE));
    }

    @Test
    void nearbyLandingIsOk() {
        assertEquals(UseMilestone.Arrival.OK,
                UseMilestone.classifyArrival(new Coord2d(0, 0), new Coord2d(10, 0), TOLERANCE));
    }

    @Test
    void peekBailAndWrongPlaceMustStopForagerInsteadOfReturningSuccess() {
        assertFalse(UseMilestone.resultAfterPeekBail().IsSuccess(),
                "peek-bail SUCCESS lets Forager keep walking from home");
        assertFalse(UseMilestone.resultAfterConfirmedWrongPlace(Results.SUCCESS()).IsSuccess(),
                "hearth recovery SUCCESS lets Forager continue the route from hearth");
        assertFalse(UseMilestone.resultAfterConfirmedWrongPlace(Results.FAIL()).IsSuccess());
    }
}
