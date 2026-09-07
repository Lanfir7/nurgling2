package nurgling.navigation;

import haven.Coord;
import haven.Coord2d;
import haven.MapFile;
import haven.MiniMap;
import haven.ResCache;
import haven.KeyMatch;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.Hotkeys;
import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapMarkerNavigationTest {
    @Test
    void markerNavigationCanBeReboundAndDisabled() {
        HotkeyAction action = Hotkeys.action(Hotkeys.MAP_MARKER_NAVIGATE);
        InputGesture original = action.current();
        try {
            action.binding().set(InputGesture.mouse(1, KeyMatch.MODS, KeyMatch.C));
            assertTrue(Hotkeys.matchesMapMarkerNavigate(1, KeyMatch.C));
            assertTrue(MapMarkerNavigation.isTrigger(1, false, KeyMatch.C));
            assertFalse(MapMarkerNavigation.isTrigger(1, true, KeyMatch.C));
            assertFalse(Hotkeys.matchesMapMarkerNavigate(1, 0));

            action.binding().set(InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.M));
            assertTrue(MapMarkerNavigation.isTrigger(2, false, KeyMatch.M));
            assertFalse(Hotkeys.matchesMapMarkerNavigate(1, KeyMatch.C));

            action.binding().set(InputGesture.none());
            assertFalse(Hotkeys.matchesMapMarkerNavigate(2, KeyMatch.M));
        } finally {
            action.binding().set(original);
        }
    }

    @Test
    void successfulChunkNavigationStopsTheFallbackChain() throws InterruptedException {
        List<String> calls = new ArrayList<>();

        MapMarkerNavigation.Outcome outcome = MapMarkerNavigation.tryInOrder(
            () -> { calls.add("chunk"); return true; },
            () -> { calls.add("pathfinder"); return true; },
            () -> { calls.add("direct"); return true; });

        assertEquals(MapMarkerNavigation.Outcome.CHUNK_NAV, outcome);
        assertEquals(Arrays.asList("chunk"), calls);
    }

    @Test
    void unavailableStrategiesFallBackInTheRequiredOrder() throws InterruptedException {
        List<String> calls = new ArrayList<>();

        MapMarkerNavigation.Outcome outcome = MapMarkerNavigation.tryInOrder(
            () -> { calls.add("chunk"); return false; },
            () -> { calls.add("pathfinder"); return false; },
            () -> { calls.add("direct"); return true; });

        assertEquals(MapMarkerNavigation.Outcome.DIRECT, outcome);
        assertEquals(Arrays.asList("chunk", "pathfinder", "direct"), calls);
    }

    @Test
    void reportsUnavailableWhenNoNavigationMethodCanRun() throws InterruptedException {
        assertEquals(MapMarkerNavigation.Outcome.UNAVAILABLE,
            MapMarkerNavigation.tryInOrder(() -> false, () -> false, () -> false));
    }

    @Test
    void convertsMarkerInCurrentMapSegmentToSessionWorldCoordinate() {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        MapFile.Segment segment = file.new Segment(77L);
        MiniMap.Location session = new MiniMap.Location(segment, new Coord(400, 700));
        MiniMap.Location marker = new MiniMap.Location(segment, new Coord(412, 734));

        assertEquals(new Coord2d(137.5, 379.5),
            MapMarkerNavigation.worldTarget(marker, session));

        MapFile.Segment other = file.new Segment(88L);
        assertEquals(null, MapMarkerNavigation.worldTarget(
            new MiniMap.Location(other, marker.tc), session));
    }
}
