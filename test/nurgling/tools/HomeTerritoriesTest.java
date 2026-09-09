package nurgling.tools;

import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeTerritoriesTest {
    @Test
    void persistsClaimGeometryAcrossJsonRoundTrip() {
        ClaimArea area = new ClaimArea(new ClaimArea.Tile(-812345678901L, 10, 20), Arrays.asList(
                new ClaimArea.Tile(-812345678901L, 10, 20),
                new ClaimArea.Tile(-812345678901L, 11, 20),
                new ClaimArea.Tile(99123L, 0, 20)));
        HomeTerritories.Entry claim = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "", area);

        Object encoded = HomeTerritories.encode(Collections.singletonList(claim));
        Object jsonRoundTrip = new JSONObject(Collections.singletonMap("homes", encoded))
                .toMap().get("homes");

        assertEquals(Collections.singletonList(claim), HomeTerritories.decode(jsonRoundTrip));
    }

    @Test
    void repeatedCaptureExpandsTheSameClaimInsteadOfAddingAnotherHome() {
        ClaimArea savedArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 10), Arrays.asList(
                new ClaimArea.Tile(7L, 10, 10), new ClaimArea.Tile(7L, 11, 10)));
        ClaimArea currentArea = new ClaimArea(new ClaimArea.Tile(7L, 11, 10), Arrays.asList(
                new ClaimArea.Tile(7L, 11, 10), new ClaimArea.Tile(7L, 12, 10)));
        HomeTerritories.Entry saved = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "Lanfir", savedArea);
        HomeTerritories.Entry current = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "", currentArea);

        List<HomeTerritories.Entry> merged = HomeTerritories.merge(
                Collections.singletonList(saved), Collections.singletonList(current));

        assertEquals(1, merged.size());
        assertEquals("Lanfir", merged.get(0).name);
        assertEquals(savedArea.anchor, merged.get(0).area.anchor);
        assertEquals(3, merged.get(0).area.size());
    }

    @Test
    void checksVillageAndPersonalClaimIndependently() {
        ClaimArea homeArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 10), Arrays.asList(
                new ClaimArea.Tile(7L, 10, 10), new ClaimArea.Tile(7L, 11, 10)));
        List<HomeTerritories.Entry> saved = Arrays.asList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Moria"),
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "", homeArea));
        List<HomeTerritories.Entry> current = Arrays.asList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Moria"),
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "", homeArea));

        HomeTerritories.HomeStatus atHome = HomeTerritories.status(saved, current, homeArea);
        HomeTerritories.HomeStatus atMarket = HomeTerritories.status(saved,
                Collections.singletonList(new HomeTerritories.Entry(
                        HomeTerritories.Type.VILLAGE, "Market Town")),
                new ClaimArea(new ClaimArea.Tile(8L, 10, 10), Collections.singletonList(
                        new ClaimArea.Tile(8L, 10, 10))));
        HomeTerritories.HomeStatus villageOnly = HomeTerritories.status(saved,
                Collections.singletonList(new HomeTerritories.Entry(
                        HomeTerritories.Type.VILLAGE, "Moria")),
                new ClaimArea(new ClaimArea.Tile(8L, 10, 10), Collections.singletonList(
                        new ClaimArea.Tile(8L, 10, 10))));
        HomeTerritories.HomeStatus claimOnly = HomeTerritories.status(saved,
                Collections.singletonList(new HomeTerritories.Entry(
                        HomeTerritories.Type.VILLAGE, "Market Town")), homeArea);

        assertTrue(atHome.village);
        assertTrue(atHome.claim);
        assertFalse(atMarket.village);
        assertFalse(atMarket.claim);
        assertTrue(villageOnly.village);
        assertFalse(villageOnly.claim);
        assertFalse(claimOnly.village);
        assertTrue(claimOnly.claim);
    }

    @Test
    void matchingHomesSuppliesTheSameVillageAndClaimHitsAsStatus() {
        ClaimArea homeArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 10), Arrays.asList(
                new ClaimArea.Tile(7L, 10, 10), new ClaimArea.Tile(7L, 11, 10)));
        HomeTerritories.Entry savedVillage = new HomeTerritories.Entry(
                HomeTerritories.Type.VILLAGE, "Moria");
        HomeTerritories.Entry savedClaim = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "", homeArea);
        List<HomeTerritories.Entry> saved = Arrays.asList(savedVillage, savedClaim);
        List<HomeTerritories.Entry> current = Arrays.asList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Moria"),
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "", homeArea));

        assertEquals(saved, HomeTerritories.matchingHomes(saved, current, homeArea));
        assertEquals(Collections.singletonList(savedVillage),
                HomeTerritories.matchingHomes(saved,
                        Collections.singletonList(new HomeTerritories.Entry(
                                HomeTerritories.Type.VILLAGE, "Moria")),
                        new ClaimArea(new ClaimArea.Tile(8L, 10, 10), Collections.singletonList(
                                new ClaimArea.Tile(8L, 10, 10)))));
        assertEquals(Collections.singletonList(savedClaim),
                HomeTerritories.matchingHomes(saved,
                        Collections.singletonList(new HomeTerritories.Entry(
                                HomeTerritories.Type.VILLAGE, "Market Town")), homeArea));

        HomeTerritories.HomeStatus atHome = HomeTerritories.status(saved, current, homeArea);
        HomeTerritories.HomeStatus villageOnly = HomeTerritories.status(saved,
                Collections.singletonList(new HomeTerritories.Entry(
                        HomeTerritories.Type.VILLAGE, "Moria")),
                new ClaimArea(new ClaimArea.Tile(8L, 10, 10), Collections.singletonList(
                        new ClaimArea.Tile(8L, 10, 10))));
        HomeTerritories.HomeStatus mixedCaseVillage = HomeTerritories.status(saved,
                Collections.singletonList(new HomeTerritories.Entry(
                        HomeTerritories.Type.VILLAGE, "moria")),
                new ClaimArea(new ClaimArea.Tile(8L, 10, 10), Collections.singletonList(
                        new ClaimArea.Tile(8L, 10, 10))));
        HomeTerritories.HomeStatus claimOnly = HomeTerritories.status(saved,
                Collections.singletonList(new HomeTerritories.Entry(
                        HomeTerritories.Type.VILLAGE, "Market Town")), homeArea);

        assertTrue(atHome.village);
        assertTrue(atHome.claim);
        assertTrue(villageOnly.village);
        assertFalse(villageOnly.claim);
        assertTrue(mixedCaseVillage.village);
        assertFalse(mixedCaseVillage.claim);
        assertFalse(claimOnly.village);
        assertTrue(claimOnly.claim);
    }

    @Test
    void mergeAndStatusTreatMixedCaseVillageAndClaimNamesAsTheSameHome() {
        HomeTerritories.Entry savedVillage = new HomeTerritories.Entry(
                HomeTerritories.Type.VILLAGE, "Moria");
        HomeTerritories.Entry currentVillage = new HomeTerritories.Entry(
                HomeTerritories.Type.VILLAGE, "moria");
        HomeTerritories.Entry savedClaim = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "Lanfir");
        HomeTerritories.Entry currentClaim = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "lanfir");

        List<HomeTerritories.Entry> merged = HomeTerritories.merge(
                Arrays.asList(savedVillage, savedClaim),
                Arrays.asList(currentVillage, currentClaim));
        HomeTerritories.HomeStatus atHome = HomeTerritories.status(
                Arrays.asList(savedVillage, savedClaim),
                Arrays.asList(currentVillage, currentClaim), null);

        assertEquals(2, merged.size());
        assertEquals("Moria", merged.get(0).name);
        assertEquals("Lanfir", merged.get(1).name);
        assertTrue(atHome.village);
        assertTrue(atHome.claim);
    }

    @Test
    void addsForeignClaimGeometryWithoutDroppingDetectedVillage() {
        ClaimArea area = new ClaimArea(new ClaimArea.Tile(7L, 10, 10),
                Collections.singletonList(new ClaimArea.Tile(7L, 10, 10)));
        List<HomeTerritories.Entry> village = Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Moria"));

        List<HomeTerritories.Entry> detected = HomeTerritories.withCurrentClaim(
                village, Collections.emptyList(), area, null);

        assertEquals(2, detected.size());
        assertEquals(HomeTerritories.Type.VILLAGE, detected.get(0).type);
        assertEquals(HomeTerritories.Type.CLAIM, detected.get(1).type);
        assertEquals("", detected.get(1).name);
        assertEquals(area, detected.get(1).area);
    }

    @Test
    void reusesSavedClaimNameWhenForeignClaimGeometryMatches() {
        ClaimArea savedArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 10),
                Collections.singletonList(new ClaimArea.Tile(7L, 10, 10)));
        ClaimArea currentArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 11), Arrays.asList(
                new ClaimArea.Tile(7L, 10, 10), new ClaimArea.Tile(7L, 10, 11)));
        HomeTerritories.Entry saved = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "Lanfir", savedArea);

        List<HomeTerritories.Entry> detected = HomeTerritories.withCurrentClaim(
                Collections.emptyList(), Collections.singletonList(saved), currentArea, null);

        assertEquals(1, detected.size());
        assertEquals("Lanfir", detected.get(0).name);
        assertEquals(2, detected.get(0).area.size());
        assertEquals(savedArea.anchor, detected.get(0).area.anchor);
    }

    @Test
    void keepsHomesIsolatedByGameWorld() {
        HomeTerritories.Entry worldOneClaim =
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir");
        HomeTerritories.Entry worldTwoVillage =
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oakvale");

        Object stored = HomeTerritories.encodeForWorld(null, "world-one",
                Collections.singletonList(worldOneClaim));
        stored = HomeTerritories.encodeForWorld(stored, "world-two",
                Collections.singletonList(worldTwoVillage));
        String json = new JSONObject(Collections.singletonMap("homeTerritories", stored)).toString();
        stored = new JSONObject(json).toMap().get("homeTerritories");

        assertEquals(Collections.singletonList(worldOneClaim),
                HomeTerritories.decodeForWorld(stored, "world-one"));
        assertEquals(Collections.singletonList(worldTwoVillage),
                HomeTerritories.decodeForWorld(stored, "world-two"));
        assertEquals(Collections.emptyList(),
                HomeTerritories.decodeForWorld(stored, "world-three"));
    }

    @Test
    void persistsTypeAndNameAndSkipsMalformedEntries() {
        HomeTerritories.Entry claim = new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir");
        HomeTerritories.Entry village = new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oakvale");
        List<Object> stored = HomeTerritories.encode(Arrays.asList(claim, village));
        stored.add(Collections.singletonMap("type", "REALM"));
        stored.add("broken");

        assertEquals(Arrays.asList(claim, village), HomeTerritories.decode(stored));
    }

    @Test
    void mergesWithoutDuplicatingSavedTerritories() {
        HomeTerritories.Entry claim = new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir");
        HomeTerritories.Entry village = new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oakvale");

        assertEquals(Arrays.asList(claim, village),
                HomeTerritories.merge(Collections.singletonList(claim), Arrays.asList(claim, village)));
    }

    @Test
    void appliesOpenSettingsEditsWithoutDroppingConcurrentAutosaves() {
        HomeTerritories.Entry claim = new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir");
        HomeTerritories.Entry oldVillage = new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Old Town");
        HomeTerritories.Entry hearthVillage = new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Hearth Town");

        assertEquals(Arrays.asList(oldVillage, hearthVillage), HomeTerritories.applyEdits(
                Arrays.asList(claim, oldVillage, hearthVillage),
                Arrays.asList(claim, oldVillage),
                Collections.singletonList(oldVillage)));
    }

    @Test
    void deletingClaimFromOpenSettingsRemovesItsExpandedGeometry() {
        ClaimArea originalArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 10),
                Collections.singletonList(new ClaimArea.Tile(7L, 10, 10)));
        ClaimArea expandedArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 10), Arrays.asList(
                new ClaimArea.Tile(7L, 10, 10), new ClaimArea.Tile(7L, 11, 10)));
        HomeTerritories.Entry original = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "Lanfir", originalArea);
        HomeTerritories.Entry expanded = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "Lanfir", expandedArea);

        assertEquals(Collections.emptyList(), HomeTerritories.applyEdits(
                Collections.singletonList(expanded), Collections.singletonList(original),
                Collections.emptyList()));
    }

    @Test
    void formatsClaimAndVillageLabels() {
        assertEquals("Lanfir's Claim",
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir").displayName());
        assertEquals("Oakvale",
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oakvale").displayName());
    }

    @Test
    void detectsClaimAndVillageAndIgnoresRealm() {
        Map<Integer, String> owners = new LinkedHashMap<>();
        owners.put(10, "Lanfir");
        owners.put(20, "Oakvale");
        owners.put(30, "Northern Realm");

        Map<Integer, List<String>> tags = new LinkedHashMap<>();
        tags.put(10, Collections.singletonList("cplot"));
        tags.put(20, Collections.singletonList("vlg"));
        tags.put(30, Arrays.asList("prov", "realm"));

        assertEquals(Arrays.asList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir"),
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oakvale")
        ), HomeTerritories.detect(owners, tags::get));
    }

    @Test
    void classifiesArbitraryOwnerIdsFromActiveClaimOverlay() {
        Map<Integer, String> owners = new LinkedHashMap<>();
        owners.put(84721, "Lanfir");

        assertEquals(Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir")
        ), HomeTerritories.classifyOwners(owners.values(),
                Collections.singletonList(HomeTerritories.Type.CLAIM),
                Collections.emptySet(), Collections.emptySet(), "Lanfir",
                Collections.emptyList()));
    }

    @Test
    void classifiesSingleOwnerFromActiveVillageOverlay() {
        assertEquals(Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oakvale")
        ), HomeTerritories.classifyOwners(Collections.singletonList("Oakvale"),
                Collections.singletonList(HomeTerritories.Type.VILLAGE),
                Collections.emptySet(), Collections.emptySet(), "Lanfir",
                Collections.emptyList()));
    }

    @Test
    void classifiesOverlappingClaimAndVillageUsingKnownVillage() {
        assertEquals(Arrays.asList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Trader"),
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oakvale")
        ), HomeTerritories.classifyOwners(Arrays.asList("Trader", "Oakvale"),
                Arrays.asList(HomeTerritories.Type.CLAIM, HomeTerritories.Type.VILLAGE),
                Collections.singleton("Oakvale"), Collections.emptySet(), "Lanfir",
                Collections.emptyList()));
    }

    @Test
    void excludesRealmOwnerBeforeMatchingActiveHomeTerritories() {
        assertEquals(Arrays.asList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir"),
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oakvale")
        ), HomeTerritories.classifyOwners(Arrays.asList("Northern Realm", "Lanfir", "Oakvale"),
                Arrays.asList(HomeTerritories.Type.CLAIM, HomeTerritories.Type.VILLAGE),
                Collections.singleton("Oakvale"), Collections.singleton("Northern Realm"), "Lanfir",
                Collections.emptyList()));
    }

    @Test
    void usesSavedTerritoryAsTypeHintWhenBothOverlaysAreActive() {
        List<HomeTerritories.Entry> saved = Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Market Town"));

        assertEquals(Arrays.asList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Merchant"),
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Market Town")
        ), HomeTerritories.classifyOwners(Arrays.asList("Market Town", "Merchant"),
                Arrays.asList(HomeTerritories.Type.CLAIM, HomeTerritories.Type.VILLAGE),
                Collections.emptySet(), Collections.emptySet(), "Lanfir", saved));
    }

    @Test
    void doesNotAssignOneOwnerToBothOverlappingTerritories() {
        assertEquals(Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir")
        ), HomeTerritories.classifyOwners(Collections.singletonList("Lanfir"),
                Arrays.asList(HomeTerritories.Type.CLAIM, HomeTerritories.Type.VILLAGE),
                Collections.emptySet(), Collections.emptySet(), "Lanfir",
                Collections.emptyList()));
    }

    @Test
    void keepsKnownVillageWhenOverlappingClaimOwnerIsUnavailable() {
        assertEquals(Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Moria")
        ), HomeTerritories.classifyOwners(Collections.singletonList("Moria"),
                Arrays.asList(HomeTerritories.Type.CLAIM, HomeTerritories.Type.VILLAGE),
                Collections.singleton("Moria"), Collections.emptySet(), "Lanfear",
                Collections.emptyList()));
    }

    @Test
    void doesNotGuessWhenAnUnknownRealmCouldBeAmongOwners() {
        assertEquals(Collections.emptyList(), HomeTerritories.classifyOwners(
                Arrays.asList("Foreign Realm", "Market Town"),
                Collections.singletonList(HomeTerritories.Type.VILLAGE),
                Collections.emptySet(), Collections.emptySet(), "Lanfir",
                Collections.emptyList()));
    }

    @Test
    void doesNotGuessClaimAndVillageFromOwnerOrder() {
        assertEquals(Collections.emptyList(), HomeTerritories.classifyOwners(
                Arrays.asList("First Owner", "Second Owner"),
                Arrays.asList(HomeTerritories.Type.CLAIM, HomeTerritories.Type.VILLAGE),
                Collections.emptySet(), Collections.emptySet(), "Lanfir",
                Collections.emptyList()));
    }

    @Test
    void doesNotUseUnknownRealmAsTheRemainingClaimOwner() {
        assertEquals(Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Moria")
        ), HomeTerritories.classifyOwners(Arrays.asList("Moria", "Foreign Realm"),
                Arrays.asList(HomeTerritories.Type.CLAIM, HomeTerritories.Type.VILLAGE),
                Collections.singleton("Moria"), Collections.emptySet(), "Lanfear",
                Collections.emptyList(), false));
    }
}
