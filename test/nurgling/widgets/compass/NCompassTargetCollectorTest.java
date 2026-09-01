package nurgling.widgets.compass;

import haven.Coord2d;
import haven.Widget;
import haven.res.ui.locptr.Pointer;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NCompassTargetCollectorTest {
    @Test
    void findsEachPointerOnlyOnceAcrossOverlappingRoots() {
        Widget root = new Widget();
        Widget nested = root.add(new Widget());
        Pointer pointer = nested.add(new Pointer(null));

        assertEquals(Collections.singletonList(pointer),
                NCompassTargetCollector.findPointers(root, nested));
    }

    @Test
    void partyNamePrefersCacheThenBuddyThenFallback() {
        assertEquals("Cached", NCompassTargetCollector.choosePartyName("Cached", "Buddy", "Party member"));
        assertEquals("Buddy", NCompassTargetCollector.choosePartyName(null, "Buddy", "Party member"));
        assertEquals("Party member", NCompassTargetCollector.choosePartyName("", "", "Party member"));
    }

    @Test
    void questPointerUsesCurrentGobPositionInsteadOfStalePointerCoordinates() {
        Coord2d stalePointerPosition = new Coord2d(100, 200);
        Coord2d currentGobPosition = new Coord2d(-300, -400);

        assertEquals(currentGobPosition,
                NCompassTargetCollector.choosePointerPosition(stalePointerPosition, currentGobPosition));
    }
}
