package haven;

import haven.MapFile.PMarker;
import haven.MapFile.SMarker;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapMarkerVisibilityTest {
    private static SMarker thingwall(MapFile file, Coord tc) {
        return new SMarker(file, 42L, tc, "Thingwall", UID.nil,
                new Resource.Saved(Resource.remote(), "gfx/terobjs/mm/thingwall", 1), new byte[0]);
    }

    @Test
    void resourceIdentityIsStableAcrossResourceVersions() {
        MapFile file = new MapFile(null, "");
        SMarker first = thingwall(file, Coord.of(10, 20));
        SMarker newer = new SMarker(file, 42L, Coord.of(10, 20), "Thingwall", UID.nil,
                new Resource.Saved(Resource.remote(), "gfx/terobjs/mm/thingwall", 99), new byte[0]);

        assertEquals(MapMarkerVisibility.identity(first), MapMarkerVisibility.identity(newer));
    }

    @Test
    void mapFileMarkersKeepTheirExistingControls() {
        MapFile file = new MapFile(null, "");
        PMarker marker = new PMarker(file, 1L, Coord.z, "Flag", Color.YELLOW, false);
        Set<String> hidden = Collections.singleton(MapMarkerVisibility.identity(marker));

        assertFalse(MapMarkerVisibility.isSystemMarker(marker));
        assertTrue(MapMarkerVisibility.visible(false, hidden, marker));
        assertTrue(MapMarkerVisibility.visible(true, hidden, marker));
    }

    @Test
    void systemMapFileMarkersRemainCustomFilterable() {
        MapFile file = new MapFile(null, "");
        SMarker marker = thingwall(file, Coord.z);
        Set<String> hidden = Collections.singleton(MapMarkerVisibility.identity(marker));

        assertTrue(MapMarkerVisibility.isSystemMarker(marker));
        assertFalse(MapMarkerVisibility.visible(true, hidden, marker));
    }

    @Test
    void dynamicResourceIdentitiesUseTheirSourceCategory() {
        String fish = MapMarkerVisibility.resourceIdentity("fish", "gfx/invobjs/fish/perch");
        String live = MapMarkerVisibility.resourceIdentity("live", "gfx/hud/mmap/questgiver");

        assertEquals("fish:gfx/invobjs/fish/perch", fish);
        assertFalse(MapMarkerVisibility.visible(true, Collections.singleton(live), live));
        assertTrue(MapMarkerVisibility.visible(false, Collections.singleton(live), live));
    }

    @Test
    void customFilterOnlyAppliesToUntoggledIconSources() {
        Set<String> hidden = new java.util.LinkedHashSet<>();
        String live = "live:gfx/hud/mmap/questgiver";
        String discovery = "discovery:gfx/terobjs/herbs/chantrelle";
        String timer = "timer:gfx/invobjs/turnip";
        String tree = "tree:gfx/terobjs/trees/oak";
        String fish = "fish:gfx/invobjs/fish/perch";
        String prospecting = "prospecting:Iron";
        String localSample = "local:Granite";
        String mapMarker = "resource:gfx/terobjs/mm/thingwall";
        Collections.addAll(hidden, live, discovery, timer, tree, fish, prospecting, localSample, mapMarker);

        assertFalse(MapMarkerVisibility.visible(true, hidden, live));
        assertFalse(MapMarkerVisibility.visible(true, hidden, discovery));
        assertFalse(MapMarkerVisibility.visible(true, hidden, timer));
        assertTrue(MapMarkerVisibility.visible(true, hidden, tree));
        assertTrue(MapMarkerVisibility.visible(true, hidden, fish));
        assertTrue(MapMarkerVisibility.visible(true, hidden, prospecting));
        assertTrue(MapMarkerVisibility.visible(true, hidden, localSample));
        assertFalse(MapMarkerVisibility.visible(true, hidden, mapMarker));
    }

    @Test
    void onlyVisitedThingwallsAreHaloEligible() {
        MapFile file = new MapFile(null, "");
        SMarker visited = thingwall(file, Coord.of(10, 20));
        SMarker unvisited = thingwall(file, Coord.of(30, 40));

        Set<String> known = Collections.singleton(MapMarkerVisibility.thingwallKey(visited));
        assertTrue(MapMarkerVisibility.hasVisitedThingwallHalo(known, visited));
        assertFalse(MapMarkerVisibility.hasVisitedThingwallHalo(known, unvisited));
    }

    @Test
    void persistedSelectionsRoundTrip() {
        Set<String> hidden = new java.util.LinkedHashSet<>();
        hidden.add("resource:gfx/terobjs/mm/thingwall");
        hidden.add("placed:ffffff00");

        assertEquals(hidden, MapMarkerVisibility.decode(MapMarkerVisibility.encode(hidden)));
    }

    @Test
    void mergedVisitedThingwallsKeepDiscoveriesFromEveryMapInstance() {
        Set<String> firstMap = Collections.singleton("map-a|resource:thingwall@1:10:20");
        Set<String> secondMap = Collections.singleton("map-a|resource:thingwall@1:30:40");

        Set<String> merged = MapMarkerVisibility.merge(firstMap, secondMap);
        assertTrue(merged.containsAll(firstMap));
        assertTrue(merged.containsAll(secondMap));
    }
}
