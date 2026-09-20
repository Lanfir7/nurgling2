package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftAtlasStationQualityUpdateTest {
    @Test
    void stationKeysIncludeOnlyStationRequirementsViaQualityFormula() {
        CraftAtlasEntry.Requirement anvil = requirement(CraftAtlasEntry.RequirementKind.STATION,
                "gfx/invobjs/anvil", "Anvil");
        CraftAtlasEntry mixed = CraftAtlasEntry.builder("paginae/craft/test", "Test")
                .requirement(anvil)
                .requirement(requirement(CraftAtlasEntry.RequirementKind.TOOL,
                        "gfx/invobjs/smithshammer", "Smithy's Hammer"))
                .requirement(requirement(CraftAtlasEntry.RequirementKind.SKILL,
                        "gfx/hud/chr/masonry", "Masonry"))
                .requirement(requirement(CraftAtlasEntry.RequirementKind.DISCOVERY,
                        "gfx/invobjs/mystery", "Unknown"))
                .build();

        Set<String> keys = CraftAtlasStationQualityUpdate.stationKeys(Collections.singletonList(mixed));

        assertEquals(Collections.singleton(CraftAtlasQualityFormula.key(anvil)), keys);
        assertFalse(keys.contains("tool:smithy-hammer"));
    }

    @Test
    void mapsInspectedAnvilGobToStationAnvilAndStoresHigherQualityAtHome() {
        Set<String> keys = stationKeysFor(anvilEntry());
        Map<String, Double> stored = new LinkedHashMap<>();

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/anvil", 87, true, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.UPDATED, result.decision);
        assertEquals("station:anvil", result.key);
        assertTrue(result.updated());
        assertEquals(Collections.singletonMap("station:anvil", 87.0), stored);
    }

    @Test
    void inspectedProcessingGobUpdatesCanonicalToolBackedStation() {
        Set<String> keys = stationKeysFor(CraftAtlasEntry.builder("mince", "Mince")
                .requirement(requirement(CraftAtlasEntry.RequirementKind.TOOL,
                        "wiki-item:meatgrinder", "Meatgrinder")).build());
        Map<String, Double> stored = new LinkedHashMap<>();

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/meatgrinder", 87, true, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.UPDATED, result.decision);
        assertEquals(87.0, stored.get("station:meatgrinder"));
    }

    @Test
    void inspectedGenericCauldronKeepsMetalAndClayQualitySeparate() {
        CraftAtlasEntry generic = CraftAtlasEntry.builder("boil", "Boil")
                .requirement(requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "wiki-item:cauldron", "Cauldron")).build();
        Set<String> keys = stationKeysFor(generic);
        Map<String, Double> stored = new LinkedHashMap<>();

        CraftAtlasStationQualityUpdate.apply("gfx/terobjs/cauldron", 70, true, keys, stored);
        CraftAtlasStationQualityUpdate.apply("gfx/terobjs/claycauldron", 90, true, keys, stored);

        assertEquals(70.0, stored.get("station:cauldron"));
        assertEquals(90.0, stored.get("station:clay-cauldron"));
    }

    @Test
    void inspectedActualSpinningWheelResourceUpdatesItsQuality() {
        Set<String> keys = stationKeysFor(CraftAtlasEntry.builder("yarn", "Yarn")
                .requirement(requirement(CraftAtlasEntry.RequirementKind.STATION,
                        "wiki-item:spinning-wheel", "Spinning Wheel")).build());
        Map<String, Double> stored = new LinkedHashMap<>();

        assertTrue(CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/swheel", 87, true, keys, stored).updated());
        assertEquals(87.0, stored.get("station:spinning-wheel"));
        assertEquals("station:spinning-wheel", CraftAtlasQualityFormula.key(requirement(
                CraftAtlasEntry.RequirementKind.STATION, "gfx/terobjs/swheel", null)));
    }

    @Test
    void equalQualityLeavesStoredAnvilUnchanged() {
        assertUnchangedAnvil(87, 87);
    }

    @Test
    void lowerQualityLeavesStoredAnvilUnchanged() {
        assertUnchangedAnvil(87, 40);
    }

    @Test
    void strictlyLargerQualityUpdatesStoredAnvil() {
        Set<String> keys = stationKeysFor(anvilEntry());
        Map<String, Double> stored = storedAnvil(87);

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/anvil", 88, true, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.UPDATED, result.decision);
        assertEquals("station:anvil", result.key);
        assertTrue(result.updated());
        assertEquals(88.0, stored.get("station:anvil"));
    }

    @Test
    void ignoresSmithyHammerToolGob() {
        assertIgnored("gfx/invobjs/smithshammer", 99, true, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void ignoresWhenCatalogHasOnlyTools() {
        CraftAtlasEntry toolsOnly = CraftAtlasEntry.builder("paginae/craft/hammer", "Hammer")
                .requirement(requirement(CraftAtlasEntry.RequirementKind.TOOL,
                        "gfx/invobjs/smithshammer", "Smithy's Hammer"))
                .build();
        assertIgnored("gfx/terobjs/anvil", 99, true, stationKeysFor(toolsOnly), storedAnvil(87));
    }

    @Test
    void ignoresUnknownPalisadeGob() {
        assertIgnored("gfx/terobjs/palisade", 99, true, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void ignoresNullGobResource() {
        assertIgnored(null, 99, true, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void ignoresBlankGobResource() {
        assertIgnored("  ", 99, true, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void ignoresWhenNotAtHomeEvenIfQualityIsHigher() {
        assertIgnored("gfx/terobjs/anvil", 99, false, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void ignoresQualityBelowOne() {
        assertIgnored("gfx/terobjs/anvil", 0.9, true, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void bundledCatalogExposesOvenAndAnvilStationKeys() {
        Set<String> keys = CraftAtlasStationQualityUpdate.stationKeys(WikiReferenceCatalog.loadBundled());
        assertTrue(keys.contains(bundledFormulaKey("Oven")));
        assertTrue(keys.contains("station:anvil"));
    }

    @Test
    void inspectingBundledOvenUpdatesExactFormulaKey() {
        assertInspectedGobUpdatesExactBundledKey("gfx/terobjs/oven", "Oven");
    }

    @Test
    void inspectingBundledHerbalistTableGobUpdatesExactFormulaKey() {
        assertInspectedGobUpdatesExactBundledKey("gfx/terobjs/htable", "Herbalist Table");
    }

    @Test
    void inspectingBundledTarKilnGobUpdatesExactFormulaKey() {
        assertInspectedGobUpdatesExactBundledKey("gfx/terobjs/tarkiln", "Tar Kiln");
    }

    @Test
    void inspectingBundledGridironGobUpdatesFireFireplaceGridIronFormulaKey() {
        assertInspectedGobUpdatesExactBundledKey("gfx/terobjs/gridiron",
                "Fire (lit) or Fireplace (lit) or Grid Iron (assembled)");
    }

    @Test
    void powUpdatesAllBundledFireCompatibleKeysButNotFireplaceOnly() {
        Set<String> fireKeys = bundledKeysWithAlternative("fire");
        Set<String> fireplaceOnly = bundledFireplaceOnlyKeys();
        assertFalse(fireKeys.isEmpty());
        assertFalse(fireplaceOnly.isEmpty());

        Map<String, Double> stored = applyBundledGob("gfx/terobjs/pow", 55);

        for(String key : fireKeys) assertEquals(55.0, stored.get(key), key);
        for(String key : fireplaceOnly) assertFalse(stored.containsKey(key), key);
    }

    @Test
    void fireplaceUpdatesFireplacePlusFireOrFireplaceVariants() {
        Set<String> fireplaceKeys = bundledKeysWithAlternative("fireplace");
        Set<String> fireOnly = bundledFireOnlyKeys();
        assertFalse(fireplaceKeys.isEmpty());
        assertFalse(fireOnly.isEmpty());

        Map<String, Double> stored = applyBundledGob("gfx/terobjs/fireplace", 61);

        for(String key : fireplaceKeys) assertEquals(61.0, stored.get(key), key);
        for(String key : fireOnly) assertFalse(stored.containsKey(key), key);
    }

    @Test
    void strictMaxAppliesIndependentlyPerSatisfiedFireKey() {
        String fireKey = bundledFormulaKey("Fire");
        String orKey = bundledFireOrFireplaceKey();
        Set<String> keys = CraftAtlasStationQualityUpdate.stationKeys(WikiReferenceCatalog.loadBundled());
        Map<String, Double> stored = new LinkedHashMap<String, Double>();
        stored.put(fireKey, 90.0);
        stored.put(orKey, 10.0);

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/pow", 50, true, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.UPDATED, result.decision);
        assertEquals(90.0, stored.get(fireKey));
        assertEquals(50.0, stored.get(orKey));
    }

    @Test
    void rollbackRestoresEveryChangedBundledFireKey() {
        Set<String> fireKeys = bundledKeysWithAlternative("fire");
        Set<String> keys = CraftAtlasStationQualityUpdate.stationKeys(WikiReferenceCatalog.loadBundled());
        Map<String, Double> stored = new LinkedHashMap<String, Double>();
        stored.put(bundledFormulaKey("Fire"), 12.0);
        Map<String, Double> before = new LinkedHashMap<String, Double>(stored);

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/pow", 44, true, keys, stored);

        assertTrue(result.updated());
        for(String key : fireKeys) assertEquals(44.0, stored.get(key), key);
        result.rollback();
        assertEquals(before, stored);
    }

    @Test
    void mapsInspectedFirePlaceGobToExistingStationFire() {
        assertMappedGob("gfx/terobjs/pow", "station:fire", fireEntry());
    }

    @Test
    void mapsInspectedHerbalistTableGobToExistingStationHerbalistTable() {
        assertMappedGob("gfx/terobjs/htable", "station:herbalist-table", herbalistTableEntry());
    }

    @Test
    void mapsInspectedTarKilnGobToExistingStationTarKiln() {
        assertMappedGob("gfx/terobjs/tarkiln", "station:tar-kiln", tarKilnEntry());
    }

    @Test
    void ignoresNaNQuality() {
        assertIgnored("gfx/terobjs/anvil", Double.NaN, true, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void ignoresPositiveInfinityQuality() {
        assertIgnored("gfx/terobjs/anvil", Double.POSITIVE_INFINITY, true, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void ignoresNegativeInfinityQuality() {
        assertIgnored("gfx/terobjs/anvil", Double.NEGATIVE_INFINITY, true, stationKeysFor(anvilEntry()), storedAnvil(87));
    }

    @Test
    void rollbackRestoresExactPriorMapValueAfterPersistenceFailure() {
        Set<String> keys = stationKeysFor(anvilEntry());
        Map<String, Double> stored = storedAnvil(87);

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/anvil", 99, true, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.UPDATED, result.decision);
        assertEquals(99.0, stored.get("station:anvil"));
        result.rollback();
        assertEquals(Collections.singletonMap("station:anvil", 87.0), stored);
    }

    @Test
    void rollbackRemovesNewlyInsertedValueAfterPersistenceFailure() {
        Set<String> keys = stationKeysFor(anvilEntry());
        Map<String, Double> stored = new LinkedHashMap<>();

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/anvil", 99, true, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.UPDATED, result.decision);
        assertEquals(99.0, stored.get("station:anvil"));
        result.rollback();
        assertTrue(stored.isEmpty());
    }

    @Test
    void repeatedFallbackStationKeyRequestsInvokeBundledLoaderOnceOnly() {
        AtomicInteger loads = new AtomicInteger();
        CraftAtlasEntry bundled = fireEntry();
        java.util.function.Supplier<Iterable<CraftAtlasEntry>> loader = () -> {
            loads.incrementAndGet();
            return Collections.singletonList(bundled);
        };
        CraftAtlasStationQualityUpdate.CachedStationKeys cache =
                new CraftAtlasStationQualityUpdate.CachedStationKeys();

        Set<String> first = cache.get(loader);
        Set<String> second = cache.get(loader);

        assertEquals(1, loads.get());
        assertEquals(first, second);
        assertTrue(first.contains("station:fire"));
    }

    private static void assertMappedGob(String gobResource, String stationKey, CraftAtlasEntry catalogEntry) {
        Set<String> keys = stationKeysFor(catalogEntry);
        Map<String, Double> stored = new LinkedHashMap<>();

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                gobResource, 42, true, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.UPDATED, result.decision);
        assertEquals(stationKey, result.key);
        assertTrue(result.updated());
        assertEquals(Collections.singletonMap(stationKey, 42.0), stored);
        assertTrue(keys.contains(stationKey));
    }

    private static void assertUnchangedAnvil(double storedQuality, double inspected) {
        Set<String> keys = stationKeysFor(anvilEntry());
        Map<String, Double> stored = storedAnvil(storedQuality);

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                "gfx/terobjs/anvil", inspected, true, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.UNCHANGED, result.decision);
        assertEquals("station:anvil", result.key);
        assertFalse(result.updated());
        assertEquals(Collections.singletonMap("station:anvil", storedQuality), stored);
    }

    private static void assertIgnored(String gobResource, double quality, boolean atHome,
                                       Set<String> keys, Map<String, Double> stored) {
        Map<String, Double> before = new LinkedHashMap<>(stored);

        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                gobResource, quality, atHome, keys, stored);

        assertEquals(CraftAtlasStationQualityUpdate.Decision.IGNORED, result.decision);
        assertFalse(result.updated());
        assertEquals(before, stored);
    }

    private static Set<String> stationKeysFor(CraftAtlasEntry... entries) {
        return CraftAtlasStationQualityUpdate.stationKeys(Arrays.asList(entries));
    }

    private static CraftAtlasEntry anvilEntry() {
        return CraftAtlasEntry.builder("paginae/craft/bar", "Bar")
                .requirement(requirement(CraftAtlasEntry.RequirementKind.STATION, "gfx/invobjs/anvil", "Anvil"))
                .build();
    }

    private static CraftAtlasEntry fireEntry() {
        return CraftAtlasEntry.builder("paginae/craft/roast", "Roast")
                .requirement(requirement(CraftAtlasEntry.RequirementKind.STATION, "fire", "Fire"))
                .build();
    }

    private static CraftAtlasEntry herbalistTableEntry() {
        return CraftAtlasEntry.builder("paginae/craft/herbal", "Herbal")
                .requirement(requirement(CraftAtlasEntry.RequirementKind.STATION, "herbalist-table",
                        "Herbalist Table"))
                .build();
    }

    private static CraftAtlasEntry tarKilnEntry() {
        return CraftAtlasEntry.builder("paginae/craft/tar", "Tar")
                .requirement(requirement(CraftAtlasEntry.RequirementKind.STATION, "tar-kiln", "Tar Kiln"))
                .build();
    }

    private static Map<String, Double> storedAnvil(double quality) {
        Map<String, Double> stored = new LinkedHashMap<>();
        stored.put("station:anvil", quality);
        return stored;
    }

    private static CraftAtlasEntry.Requirement requirement(CraftAtlasEntry.RequirementKind kind,
                                                          String resource, String name) {
        return new CraftAtlasEntry.Requirement(kind, resource, name, null);
    }

    private static void assertInspectedGobUpdatesExactBundledKey(String gobResource, String stationName) {
        String expected = bundledFormulaKey(stationName);
        Map<String, Double> stored = applyBundledGob(gobResource, 77);

        assertEquals(expected, stored.keySet().iterator().next());
        assertEquals(77.0, stored.get(expected));
        assertEquals(1, stored.size());
    }

    private static Map<String, Double> applyBundledGob(String gobResource, double quality) {
        Set<String> keys = CraftAtlasStationQualityUpdate.stationKeys(WikiReferenceCatalog.loadBundled());
        Map<String, Double> stored = new LinkedHashMap<String, Double>();
        CraftAtlasStationQualityUpdate.Result result = CraftAtlasStationQualityUpdate.apply(
                gobResource, quality, true, keys, stored);
        assertEquals(CraftAtlasStationQualityUpdate.Decision.UPDATED, result.decision);
        assertTrue(result.updated());
        return stored;
    }

    private static String bundledFormulaKey(String stationName) {
        for(CraftAtlasEntry entry : WikiReferenceCatalog.loadBundled()) {
            if(entry == null) continue;
            for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
                if(requirement != null && requirement.kind == CraftAtlasEntry.RequirementKind.STATION
                        && stationName.equals(requirement.name))
                    return CraftAtlasQualityFormula.key(requirement);
            }
        }
        throw new AssertionError("bundled catalog missing station " + stationName);
    }

    private static String bundledFireOrFireplaceKey() {
        for(CraftAtlasEntry.Requirement requirement : bundledStationRequirements()) {
            if(alternativesContain(requirement, "fire") && alternativesContain(requirement, "fireplace"))
                return CraftAtlasQualityFormula.key(requirement);
        }
        throw new AssertionError("bundled catalog missing Fire-or-Fireplace station");
    }

    private static Set<String> bundledKeysWithAlternative(String identity) {
        Set<String> keys = new LinkedHashSet<String>();
        for(CraftAtlasEntry.Requirement requirement : bundledStationRequirements()) {
            if(alternativesContain(requirement, identity))
                keys.add(CraftAtlasQualityFormula.key(requirement));
        }
        return keys;
    }

    private static Set<String> bundledFireOnlyKeys() {
        Set<String> keys = new LinkedHashSet<String>();
        for(CraftAtlasEntry.Requirement requirement : bundledStationRequirements()) {
            if(alternativesContain(requirement, "fire") && !alternativesContain(requirement, "fireplace"))
                keys.add(CraftAtlasQualityFormula.key(requirement));
        }
        return keys;
    }

    private static Set<String> bundledFireplaceOnlyKeys() {
        Set<String> keys = new LinkedHashSet<String>();
        for(CraftAtlasEntry.Requirement requirement : bundledStationRequirements()) {
            if(alternativesContain(requirement, "fireplace") && !alternativesContain(requirement, "fire"))
                keys.add(CraftAtlasQualityFormula.key(requirement));
        }
        return keys;
    }

    private static List<CraftAtlasEntry.Requirement> bundledStationRequirements() {
        List<CraftAtlasEntry.Requirement> requirements = new ArrayList<CraftAtlasEntry.Requirement>();
        for(CraftAtlasEntry entry : WikiReferenceCatalog.loadBundled()) {
            if(entry == null) continue;
            for(CraftAtlasEntry.Requirement requirement : entry.requirements) {
                if(requirement != null && requirement.kind == CraftAtlasEntry.RequirementKind.STATION)
                    requirements.add(requirement);
            }
        }
        return requirements;
    }

    private static boolean alternativesContain(CraftAtlasEntry.Requirement requirement, String identity) {
        String resource = requirement.resource == null || requirement.resource.isEmpty()
                ? requirement.name : requirement.resource;
        if(resource == null) return false;
        int cut = Math.max(resource.lastIndexOf('/'), resource.lastIndexOf(':'));
        String slug = (cut >= 0 ? resource.substring(cut + 1) : resource)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        for(String part : slug.split("-or-")) {
            if(identity.equals(part.replaceAll("-(lit|assembled)$", ""))) return true;
        }
        return false;
    }
}
