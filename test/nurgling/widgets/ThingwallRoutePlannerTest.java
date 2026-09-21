package nurgling.widgets;

import haven.Listbox;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThingwallRoutePlannerTest {
    private static ThingwallRoutePlanner.Province province(String id, String name, String... neighbors) {
        return new ThingwallRoutePlanner.Province(id, name, Arrays.asList(neighbors));
    }

    private static ThingwallRoutePlanner.Marker marker(String id, String name, int x, int y) {
        return new ThingwallRoutePlanner.Marker(id, name, 7L, x, y, true);
    }

    @Test
    void includesOnlyLearnedReachableMarkersAndCalculatesStraightDistance() {
        List<ThingwallRoutePlanner.Route> routes = ThingwallRoutePlanner.routes("a",
            Arrays.asList(province("a", "Alpha", "b"), province("b", "Bravo", "c"), province("c", "Charlie")),
            Arrays.asList(marker("a", "Alpha tree", 0, 0), marker("b", "Bravo tree", 3, 4),
                marker("c", "Charlie tree", 10, 0),
                new ThingwallRoutePlanner.Marker("x", "Other segment", 8L, 1, 1, true),
                new ThingwallRoutePlanner.Marker("z", "Unlearned", 7L, 1, 1, false)), 7L);

        assertEquals(2, routes.size());
        assertEquals("Bravo tree", routes.get(0).destinationName);
        assertEquals(Arrays.asList("a", "b"), routes.get(0).path);
        assertEquals(Arrays.asList("Alpha", "Bravo"), routes.get(0).hopNames);
        assertEquals(5.0, routes.get(0).distanceTiles);
    }

    @Test
    void choosesDeterministicShortestPathThroughCycles() {
        List<ThingwallRoutePlanner.Route> routes = ThingwallRoutePlanner.routes("a",
            Arrays.asList(province("a", "A", "c", "b"), province("b", "B", "a", "d"),
                province("c", "C", "a", "d"), province("d", "D", "b", "c")),
            Arrays.asList(marker("a", "A", 0, 0), marker("b", "B", 1, 0), marker("c", "C", 0, 1),
                marker("d", "D", 1, 1)), 7L);

        ThingwallRoutePlanner.Route destination = routes.get(2);
        assertEquals("D", destination.destinationName);
        assertEquals(Arrays.asList("a", "b", "d"), destination.path);
    }

    @Test
    void excludesDisconnectedTargetsAndSortsNamesCaseInsensitivelyWithTieBreaker() {
        List<ThingwallRoutePlanner.Route> routes = ThingwallRoutePlanner.routes("a",
            Arrays.asList(province("a", "A", "b", "c"), province("b", "B"), province("c", "C"),
                province("z", "Z")),
            Arrays.asList(marker("a", "A", 0, 0), marker("b", "alpha", 1, 0),
                marker("c", "Alpha", 2, 0), marker("z", "Disconnected", 3, 0)), 7L);

        assertEquals(2, routes.size());
        assertEquals("Alpha", routes.get(0).destinationName);
        assertEquals("alpha", routes.get(1).destinationName);
        assertTrue(routes.stream().noneMatch(route -> "Disconnected".equals(route.destinationName)));
    }

    @Test
    void returnsNoRouteWhenTheCurrentThingwallIsUnknownOrUnlearned() {
        List<ThingwallRoutePlanner.Route> routes = ThingwallRoutePlanner.routes("a",
            Arrays.asList(province("a", "A", "b"), province("b", "B")),
            Collections.singletonList(marker("b", "B", 1, 0)), 7L);

        assertTrue(routes.isEmpty());
    }

    @Test
    void keepsAdvertisedDirectRoutesWithoutGraphAndMergesThemDeterministically() {
        List<ThingwallRoutePlanner.Route> advertised = Arrays.asList(
            ThingwallRoutePlanner.directRoute("advertised:1", "Uthchik", "Source", 12.34),
            ThingwallRoutePlanner.directRoute("advertised:2", "Gillrog", "Source", 5.67));

        List<ThingwallRoutePlanner.Route> directOnly = ThingwallRoutePlanner.mergeRoutes(
            Collections.<ThingwallRoutePlanner.Route>emptyList(), advertised);
        assertEquals(Arrays.asList("Gillrog", "Uthchik"), Arrays.asList(
            directOnly.get(0).destinationName, directOnly.get(1).destinationName));
        assertEquals(12.34, directOnly.get(1).distanceTiles);
        assertEquals(Arrays.asList("Source", "Uthchik"), directOnly.get(1).hopNames);

        List<ThingwallRoutePlanner.Route> graph = ThingwallRoutePlanner.routes("a",
            Arrays.asList(province("a", "Source", "b"), province("b", "Uthchik")),
            Arrays.asList(marker("a", "Source", 0, 0), marker("b", "Uthchik", 3, 4)), 7L);
        List<ThingwallRoutePlanner.Route> merged = ThingwallRoutePlanner.mergeRoutes(graph, advertised);
        assertEquals(Arrays.asList("Gillrog", "Uthchik"), Arrays.asList(
            merged.get(0).destinationName, merged.get(1).destinationName));
        assertEquals("b", merged.get(1).destinationId);
    }

    @Test
    void reflectsTheResourceProvinceUidGraphUsedForMultiHopRoutes() {
        ThingwallProvinceFixture provinces = new ThingwallProvinceFixture();
        provinces.byid.put("surheim", new ThingwallProvinceNode("surheim", "Surheim",
            Arrays.<Object>asList("gillrog")));
        provinces.byid.put("gillrog", new ThingwallProvinceNode("gillrog", "Gillrog",
            Arrays.<Object>asList("eirgate")));
        provinces.byid.put("eirgate", new ThingwallProvinceNode("eirgate", "Eirgate",
            Collections.emptyList()));

        List<ThingwallRoutePlanner.Province> graph = ThingwallTravelFeature.reflectedProvinces(provinces);
        List<ThingwallRoutePlanner.Route> routes = ThingwallRoutePlanner.routes("surheim", graph,
            Arrays.asList(marker("surheim", "Surheim", 0, 0), marker("gillrog", "Gillrog", 3, 4),
                marker("eirgate", "Eirgate", 6, 8)), 7L);

        assertEquals(Arrays.asList("Eirgate", "Gillrog"), Arrays.asList(
            routes.get(0).destinationName, routes.get(1).destinationName));
        assertEquals(Arrays.asList("surheim", "gillrog", "eirgate"), routes.get(0).path);
        assertEquals(Arrays.asList("Surheim", "Gillrog", "Eirgate"), routes.get(0).hopNames);
    }

    @Test
    void serverConfirmedDestinationsExtendRoutesBeyondLocalHalo() {
        String scope = ThingwallKnowledgeStore.scope("char-a", "map-a", 7L);
        String saved = ThingwallKnowledgeStore.record("", scope, Arrays.asList("Gillrog", "Eirgate"));
        java.util.Set<String> confirmed = ThingwallKnowledgeStore.names(saved, scope);
        List<ThingwallRoutePlanner.Province> graph = Arrays.asList(
            province("surheim", "Surheim", "gillrog"), province("gillrog", "Gillrog", "eirgate"),
            province("eirgate", "Eirgate"));

        List<ThingwallRoutePlanner.Route> withoutConfirmation = ThingwallRoutePlanner.routes("surheim", graph,
            Arrays.asList(marker("surheim", "Surheim", 0, 0), marker("gillrog", "Gillrog", 3, 4),
                new ThingwallRoutePlanner.Marker("eirgate", "Eirgate", 7L, 6, 8, false)), 7L);
        List<ThingwallRoutePlanner.Route> withConfirmation = ThingwallRoutePlanner.routes("surheim", graph,
            Arrays.asList(marker("surheim", "Surheim", 0, 0), marker("gillrog", "Gillrog", 3, 4),
                new ThingwallRoutePlanner.Marker("eirgate", "Eirgate", 7L, 6, 8,
                    confirmed.contains("Eirgate"))), 7L);

        assertEquals(Collections.singletonList("Gillrog"), routeNames(withoutConfirmation));
        assertEquals(Arrays.asList("Eirgate", "Gillrog"), routeNames(withConfirmation));
    }

    @Test
    void serverKnowledgeIsScopedAndMergesWithoutAmbiguousMarkerNames() {
        String firstScope = ThingwallKnowledgeStore.scope("char-a", "map-a", 7L);
        String secondScope = ThingwallKnowledgeStore.scope("char-a", "map-a", 8L);
        String otherCharacterScope = ThingwallKnowledgeStore.scope("char-b", "map-a", 7L);
        String saved = ThingwallKnowledgeStore.record("", firstScope, Collections.singleton("Eirgate"));
        saved = ThingwallKnowledgeStore.record(saved, secondScope, Collections.singleton("Thyovsk"));
        saved = ThingwallKnowledgeStore.record(saved, firstScope, Collections.singleton("Gillrog"));

        assertEquals(new java.util.TreeSet<>(Arrays.asList("Eirgate", "Gillrog")),
            new java.util.TreeSet<>(ThingwallKnowledgeStore.names(saved, firstScope)));
        assertEquals(Collections.singleton("Thyovsk"), ThingwallKnowledgeStore.names(saved, secondScope));
        assertTrue(ThingwallKnowledgeStore.names(saved, otherCharacterScope).isEmpty());
        assertTrue(!ThingwallKnowledgeStore.isUniqueName("Eirgate", Arrays.asList("Eirgate", "Eirgate")));
        assertTrue(ThingwallKnowledgeStore.isUniqueName("Eirgate", Arrays.asList("Eirgate", "Gillrog")));
    }

    private static List<String> routeNames(List<ThingwallRoutePlanner.Route> routes) {
        List<String> names = new java.util.ArrayList<>();
        for(ThingwallRoutePlanner.Route route : routes)
            names.add(route.destinationName);
        return(names);
    }

    @Test
    void recognizesOnlyExactOrVersionSuffixedDynamicWidgetTypes() {
        assertTrue(ThingwallTravelFeature.matchesDynamicType("ui/thingwall", "ui/thingwall"));
        assertTrue(ThingwallTravelFeature.matchesDynamicType("ui/thingwall:12", "ui/thingwall"));
        assertTrue(ThingwallTravelFeature.matchesDynamicType("ui/provinces:1", "ui/provinces"));
        assertTrue(!ThingwallTravelFeature.matchesDynamicType("ui/thingwallish:1", "ui/thingwall"));
        assertTrue(!ThingwallTravelFeature.matchesDynamicType("ui/thingwall:x", "ui/thingwall"));
        assertTrue(!ThingwallTravelFeature.matchesDynamicType("ui/thingwall:", "ui/thingwall"));
    }

    @Test
    void destinationRowsUseScrollableListAndSelectionStartsTravel() {
        List<ThingwallRoutePlanner.Route> routes = Arrays.asList(
            ThingwallRoutePlanner.directRoute("a", "A", "Source", 1),
            ThingwallRoutePlanner.directRoute("b", "B", "Source", 2),
            ThingwallRoutePlanner.directRoute("c", "C", "Source", 3));
        ThingwallRoutePlanner.Route[] selected = new ThingwallRoutePlanner.Route[1];
        List<String> favoriteClicks = new java.util.ArrayList<>();
        ThingwallTravelFeature.RouteListState state = new ThingwallTravelFeature.RouteListState(
            route -> selected[0] = route, favoriteClicks::add, 24);

        assertSame(Listbox.class, ThingwallTravelFeature.RouteList.class.getSuperclass());
        state.routes(routes, Collections.singleton("B"));
        assertEquals(Arrays.asList("B", "A", "C"), routeNames(state.routes()));

        state.click(routes.get(1), 5);
        assertEquals(Collections.singletonList("B"), favoriteClicks);
        assertSame(null, selected[0]);

        state.click(routes.get(2), 30);
        assertSame(routes.get(2), selected[0]);

        state.routes(routes, Collections.<String>emptySet());
        assertEquals(Arrays.asList("A", "B", "C"), routeNames(state.routes()));

        state.hover(routes.get(1));
        assertSame(routes.get(1), state.hovered());
    }

    @Test
    void favoritesPersistGloballyAndToggleOff() {
        String saved = ThingwallFavorites.toggle("", "Eirgate");
        saved = ThingwallFavorites.toggle(saved, "Gillrog");

        assertEquals(new java.util.TreeSet<>(Arrays.asList("Eirgate", "Gillrog")),
            new java.util.TreeSet<>(ThingwallFavorites.names(saved)));

        saved = ThingwallFavorites.toggle(saved, "Eirgate");
        assertEquals(Collections.singleton("Gillrog"), ThingwallFavorites.names(saved));
    }
}

class ThingwallProvinceFixture {
    public final java.util.Map<Object, ThingwallProvinceNode> byid = new java.util.LinkedHashMap<>();
}

class ThingwallProvinceNode {
    public final Object id;
    public final String name;
    public final java.util.Collection<Object> neighbors;

    ThingwallProvinceNode(Object id, String name, java.util.Collection<Object> neighbors) {
        this.id = id;
        this.name = name;
        this.neighbors = neighbors;
    }
}
