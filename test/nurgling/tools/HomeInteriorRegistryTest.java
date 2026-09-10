package nurgling.tools;

import nurgling.navigation.ChunkNavManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeInteriorRegistryTest {
    private static final long HUT_INSTANCE = -7856348484756222084L;
    @Test
    void originKeysUseVillageNameClaimAnchorAndLegacyOwner() {
        HomeTerritories.Entry village = new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, " Oak Vale ");
        ClaimArea area = new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
                Collections.singleton(new ClaimArea.Tile(42L, 7, 9)));
        HomeTerritories.Entry claim = new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir", area);
        HomeTerritories.Entry legacy = new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir");

        assertEquals("village:oak vale", HomeInteriorRegistry.OriginKey.from(village).value());
        assertEquals("claim-anchor:42:7:9", HomeInteriorRegistry.OriginKey.from(claim).value());
        assertEquals("claim-owner:lanfir", HomeInteriorRegistry.OriginKey.from(legacy).value());
    }

    @Test
    void registryRoundTripPreservesBindingAndWorldIsolation() {
        HomeInteriorRegistry.Binding binding = HomeInteriorRegistry.Binding.automatic(
                "auto:42:7:9:gfx/terobjs/arch/stonemansion", 9001L,
                setOf(1001L, 1002L),
                setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9,
                        "gfx/terobjs/arch/stonemansion"),
                "Lanfir's Claim -> Stone Mansion", 1234L);
        HomeInteriorRegistry first = HomeInteriorRegistry.empty().put(binding);
        Object stored = HomeInteriorRegistry.encodeForWorld(null, "world-one", first);
        stored = HomeInteriorRegistry.encodeForWorld(stored, "world-two", HomeInteriorRegistry.empty());

        assertEquals(first, HomeInteriorRegistry.decodeForWorld(stored, "world-one"));
        assertTrue(HomeInteriorRegistry.decodeForWorld(stored, "world-two").bindings().isEmpty());
    }

    @Test
    void malformedFieldsAreIgnored() {
        HomeInteriorRegistry.Binding valid = automaticBinding(
                "auto:42:7:9:gfx/terobjs/arch/stonemansion", 9001L,
                setOf(1001L), "claim-anchor:42:7:9");
        Map<String, Object> world = HomeInteriorRegistry.encodeForWorld(
                null, "world-one", HomeInteriorRegistry.empty().put(valid));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) world.get("world-one");
        @SuppressWarnings("unchecked")
        List<Object> bindings = new ArrayList<Object>((Collection<Object>) payload.get("bindings"));
        bindings.add("broken");
        bindings.add(Collections.singletonMap("instanceId", 1));
        Map<String, Object> missingOriginType = new LinkedHashMap<String, Object>();
        missingOriginType.put("id", "auto:bad");
        missingOriginType.put("instanceId", 2L);
        missingOriginType.put("gridIds", Collections.singletonList("3"));
        missingOriginType.put("origins", Arrays.asList("not-a-key", "claim-anchor:42:7:9"));
        missingOriginType.put("rootPortal", portalMap(42L, 7, 9, "gfx/terobjs/arch/stonemansion"));
        missingOriginType.put("manual", Boolean.FALSE);
        missingOriginType.put("displayName", "Partial");
        missingOriginType.put("lastSeen", 1L);
        bindings.add(missingOriginType);
        payload.put("bindings", bindings);

        HomeInteriorRegistry decoded = HomeInteriorRegistry.decodeForWorld(world, "world-one");
        assertEquals(2, decoded.bindings().size());
        HomeInteriorRegistry.Binding recovered = decoded.findActive(3L, 2L, savedClaim());
        assertNotNull(recovered);
        assertEquals(setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                recovered.origins);
    }

    @Test
    void newerVersionYieldsEmptyRegistry() {
        HomeInteriorRegistry.Binding binding = automaticBinding(
                "auto:42:7:9:gfx/terobjs/arch/stonemansion", 9001L,
                setOf(1001L), "claim-anchor:42:7:9");
        Map<String, Object> stored = HomeInteriorRegistry.encodeForWorld(
                null, "world-one", HomeInteriorRegistry.empty().put(binding));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) stored.get("world-one");
        payload.put("version", HomeInteriorRegistry.VERSION + 1);

        assertTrue(HomeInteriorRegistry.decodeForWorld(stored, "world-one").bindings().isEmpty());
    }

    @Test
    void surfaceInstanceDoesNotMatchAnotherSurfaceGrid() {
        HomeInteriorRegistry.Binding indoor = automaticBinding(
                "auto-surface", ChunkNavManager.SURFACE_INSTANCE, setOf(1001L),
                "claim-anchor:42:7:9");
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty().put(indoor);

        assertEquals(indoor, registry.findActive(1001L, ChunkNavManager.SURFACE_INSTANCE, savedClaim()));
        assertEquals(null, registry.findActive(42L, ChunkNavManager.SURFACE_INSTANCE, savedClaim()));
    }

    @Test
    void negativeStableInstanceMatchesByInstanceFallback() {
        HomeInteriorRegistry.Binding indoor = automaticBinding(
                "auto-hut", HUT_INSTANCE, setOf(901L), "claim-anchor:42:7:9");
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty().put(indoor);

        assertEquals(indoor, registry.findActive(901L, HUT_INSTANCE, savedClaim()));
        assertEquals(indoor, registry.findActive(999L, HUT_INSTANCE, savedClaim()));
        assertEquals(indoor, registry.findActive(901L, ChunkNavManager.SURFACE_INSTANCE, savedClaim()));
        assertEquals(null, registry.findActive(42L, ChunkNavManager.SURFACE_INSTANCE, savedClaim()));
    }

    @Test
    void mergeKeepsNegativeIndoorInstance() {
        HomeInteriorRegistry.Binding existing = automaticBinding(
                "auto-hut", HUT_INSTANCE, setOf(901L), "claim-anchor:42:7:9");
        HomeInteriorRegistry.Binding incoming = automaticBinding(
                "auto-hut", ChunkNavManager.SURFACE_INSTANCE, setOf(902L),
                "claim-anchor:42:7:9");

        HomeInteriorRegistry.Binding merged = existing.merge(incoming);

        assertEquals(HUT_INSTANCE, merged.instanceId);
        assertEquals(setOf(901L, 902L), merged.gridIds);
    }

    @Test
    void exactGridMatchingPrecedesInstanceMatching() {
        HomeInteriorRegistry.Binding byGrid = automaticBinding(
                "auto-grid", 1L, setOf(100L), "claim-anchor:42:7:9");
        HomeInteriorRegistry.Binding byInstance = automaticBinding(
                "auto-instance", 2L, setOf(200L), "claim-anchor:42:7:9");
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty().put(byGrid).put(byInstance);

        assertEquals(byGrid, registry.findActive(100L, 2L, savedClaim()));
        assertEquals(byInstance, registry.findActive(999L, 2L, savedClaim()));
    }

    @Test
    void dualOriginsStayActiveIfEitherSurvives() {
        HomeInteriorRegistry.Binding binding = HomeInteriorRegistry.Binding.automatic(
                "auto:dual", 9001L, setOf(1001L),
                setOf(HomeInteriorRegistry.OriginKey.parse("village:oak vale"),
                        HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9, "gfx/terobjs/arch/stonemansion"),
                "Dual", 1L);
        List<HomeTerritories.Entry> villageOnly = Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oak Vale"));
        List<HomeTerritories.Entry> claimOnly = savedClaim();
        List<HomeTerritories.Entry> neither = Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Market"));

        assertTrue(binding.active(villageOnly));
        assertTrue(binding.active(claimOnly));
        assertFalse(binding.active(neither));
        assertFalse(binding.active(Collections.<HomeTerritories.Entry>emptyList()));
    }

    @Test
    void legacyOwnerMatchingIsCaseInsensitive() {
        HomeTerritories.Entry mixedCase = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "LANFIR");
        HomeInteriorRegistry.OriginKey key = HomeInteriorRegistry.OriginKey.parse("claim-owner:Lanfir");

        assertEquals("claim-owner:lanfir", key.value());
        assertTrue(key.matches(Collections.singletonList(mixedCase)));
        assertTrue(key.matches(savedClaim()));
        assertFalse(key.matches(Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Other"))));
    }

    @Test
    void removeWithSuppressionKeepsTombstoneAfterManualMark() {
        HomeInteriorRegistry.Binding binding = automaticBinding(
                "auto:42:7:9:gfx/terobjs/arch/stonemansion", 9001L,
                setOf(1001L), "claim-anchor:42:7:9");
        HomeInteriorRegistry.PortalIdentity portal = binding.rootPortal;
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty()
                .put(binding)
                .markManual(9001L, setOf(1001L), "Manual")
                .remove(binding.id, true);

        assertTrue(registry.bindings().isEmpty());
        assertTrue(registry.isSuppressed(portal));
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... values) {
        return new LinkedHashSet<T>(Arrays.asList(values));
    }

    private static List<HomeTerritories.Entry> savedClaim() {
        ClaimArea area = new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
                Collections.singleton(new ClaimArea.Tile(42L, 7, 9)));
        return Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir", area));
    }

    private static HomeInteriorRegistry.Binding automaticBinding(String id, long instanceId,
            Set<Long> gridIds, String origin) {
        return HomeInteriorRegistry.Binding.automatic(
                id, instanceId, gridIds,
                setOf(HomeInteriorRegistry.OriginKey.parse(origin)),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9, "gfx/terobjs/arch/stonemansion"),
                id, 1L);
    }

    private static Map<String, Object> portalMap(long gridId, int x, int y, String resource) {
        Map<String, Object> portal = new LinkedHashMap<String, Object>();
        portal.put("gridId", Long.toString(gridId));
        portal.put("x", x);
        portal.put("y", y);
        portal.put("resource", resource);
        return portal;
    }
}
