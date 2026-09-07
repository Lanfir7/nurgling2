package nurgling.hotkeys;

import haven.KeyMatch;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class Task8GameplayRebindingTest {
    @Test
    void mapWaypointUsesTheReboundMouseButton() {
        HotkeyAction action = Hotkeys.action(Hotkeys.MAP_MARKER_WAYPOINT);
        action.binding().set(InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.C));
        try {
            assertTrue(Hotkeys.matchesMapMarkerWaypoint(2, KeyMatch.C, false));
            assertTrue(Hotkeys.matchesMapMarkerWaypointModifiers(KeyMatch.C));
            AtomicBoolean handled = new AtomicBoolean();
            assertTrue(Hotkeys.dispatchMapMarkerWaypoint(2, KeyMatch.C, false,
                    () -> handled.set(true)));
            assertTrue(handled.get());
            assertFalse(Hotkeys.matchesMapMarkerWaypoint(1, KeyMatch.C, false));
            assertFalse(Hotkeys.matchesMapMarkerWaypoint(2, 0, false));
            action.binding().set(InputGesture.none());
            assertFalse(Hotkeys.matchesMapMarkerWaypoint(2, KeyMatch.C, false));
            assertFalse(Hotkeys.matchesMapMarkerWaypointModifiers(KeyMatch.C));
        } finally {
            action.binding().reset();
        }
    }

    @Test
    void mapFrameDragKeepsOrdinaryLeftButtonOnly() {
        assertTrue(Hotkeys.matchesMapMarkerWaypoint(1, 0, true));
        assertFalse(Hotkeys.matchesMapMarkerWaypoint(3, 0, true));
    }

    @Test
    void labeledMarkerDeleteUsesTheReboundMouseButton() {
        HotkeyAction action = Hotkeys.action(Hotkeys.MAP_MARKER_DELETE);
        action.binding().set(InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C));
        try {
            assertTrue(Hotkeys.matchesMapMarkerDelete(1, KeyMatch.C));
            assertFalse(Hotkeys.matchesMapMarkerDelete(3, KeyMatch.C));
            assertFalse(Hotkeys.matchesMapMarkerDelete(1, 0));
            action.binding().set(InputGesture.none());
            assertFalse(Hotkeys.matchesMapMarkerDelete(1, KeyMatch.C));
        } finally {
            action.binding().reset();
        }
    }

    @Test
    void markerDeleteWinsOverUnmodifiedForagerRecording() {
        HotkeyAction action = Hotkeys.action(Hotkeys.MAP_MARKER_DELETE);
        action.binding().set(InputGesture.mouse(1, 0, 0));
        try {
            assertTrue(Hotkeys.matchesMapMarkerDelete(1, 0));
            assertFalse(Hotkeys.allowsForagerPathRecording(1, 0));
        } finally {
            action.binding().reset();
        }
        assertTrue(Hotkeys.allowsForagerPathRecording(1, 0));
    }
}
