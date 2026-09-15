package nurgling.navigation;

import haven.Coord;
import haven.Coord2d;
import haven.KeyMatch;
import haven.MapFile;
import haven.MiniMap;
import haven.ResCache;
import haven.Gob;
import haven.Coord3f;
import haven.HomoCoord4f;
import nurgling.overlays.MarkerBeaconSprite;
import nurgling.widgets.LabeledMinimapMark;
import haven.render.BufPipe;
import haven.render.Homo3D;
import haven.render.Location;
import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.Hotkeys;
import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapMarkerBeaconTest {
    @Test
    void markerBeaconGestureCanBeReboundAndDisabled() {
        HotkeyAction action = Hotkeys.registry().find("map.marker.beacon");
        assertNotNull(action);
        InputGesture original = action.current();
        try {
            action.binding().set(InputGesture.mouse(2, KeyMatch.MODS, KeyMatch.M));
            assertTrue(Hotkeys.matchesMapMarkerBeacon(2, KeyMatch.M));
            assertFalse(Hotkeys.matchesMapMarkerBeacon(1, KeyMatch.C | KeyMatch.S));

            action.binding().set(InputGesture.none());
            assertFalse(Hotkeys.matchesMapMarkerBeacon(2, KeyMatch.M));
        } finally {
            action.binding().set(original);
        }
    }

    @Test
    void beaconUsesTheMarkerCenterRatherThanTheCursorLocation() {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        MapFile.Segment segment = file.new Segment(7L);
        MapFile.Marker marker = new MapFile.PMarker(file, segment.id, new Coord(412, 734), "Beacon", null, false);
        MiniMap.Location session = new MiniMap.Location(segment, new Coord(400, 700));

        assertEquals(new Coord2d(137.5, 379.5), MapMarkerBeacon.worldTarget(marker, session));
        assertNull(MapMarkerBeacon.worldTarget(marker, new MiniMap.Location(file.new Segment(8L), session.tc)));
    }

    @Test
    void customMarkerUsesItsPersistedTileCenter() {
        MapFile file = new MapFile(new ResCache.TestCache(), "");
        MapFile.Segment segment = file.new Segment(7L);
        MiniMap.Location session = new MiniMap.Location(segment, new Coord(400, 700));
        LabeledMinimapMark marker = new LabeledMinimapMark("q42", "Quarryartz", segment.id,
                new Coord(412, 734), null, null);

        assertEquals(new Coord2d(137.5, 379.5), MapMarkerBeacon.worldTarget(marker, session));
        assertNull(MapMarkerBeacon.worldTarget(marker, new MiniMap.Location(file.new Segment(8L), session.tc)));
        assertEquals(new Coord2d(137.5, 379.5), MapMarkerBeacon.worldTarget(segment.id,
                new Coord(412, 734), session));
        assertNull(MapMarkerBeacon.worldTarget(8L, new Coord(412, 734), session));
    }

    @Test
    void beaconLivesForTwentySecondsAndNotifiesCleanupOnce() {
        AtomicInteger finished = new AtomicInteger();
        MarkerBeaconSprite beacon = new MarkerBeaconSprite(new Gob(null, Coord2d.z), finished::incrementAndGet);

        assertFalse(beacon.tick(19.99));
        assertEquals(0, finished.get());
        assertTrue(beacon.tick(0.01));
        assertEquals(1, finished.get());
        assertTrue(beacon.tick(1));
        assertEquals(1, finished.get());
    }

    @Test
    void earlyBeaconRemovalNotifiesCleanupOnce() {
        AtomicInteger finished = new AtomicInteger();
        MarkerBeaconSprite beacon = new MarkerBeaconSprite(new Gob(null, Coord2d.z), finished::incrementAndGet);

        beacon.finish();
        beacon.finish();

        assertEquals(1, finished.get());
    }

    @Test
    void projectedBeaconVerticesStayLocalToTheTranslatedGobSlot() {
        BufPipe state = new BufPipe();
        Location.xlate(new Coord3f(100, 0, 0)).apply(state);

        HomoCoord4f projected = Homo3D.obj2clip(MarkerBeaconSprite.localVertex(0, 0, 0), state);
        assertEquals(100, projected.x, 0.001f);
    }
}
