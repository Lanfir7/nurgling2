package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeTerritoryDebugTest {
    @Test
    void describesCurrentVillageClaimAndCombinedHomeStatusFromVisibleSettings() {
        ClaimArea claimArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 20), Arrays.asList(
                new ClaimArea.Tile(7L, 10, 20),
                new ClaimArea.Tile(7L, 11, 20)));
        HomeTerritories.Entry village = new HomeTerritories.Entry(
                HomeTerritories.Type.VILLAGE, "Oakvale");
        HomeTerritories.Entry claim = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "Lanfir", claimArea);
        List<HomeTerritories.Entry> current = Arrays.asList(village, claim);
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                Collections.singletonList(claim), current, claimArea, false,
                HomeInteriorRegistry.empty(), 1L, 1L, true);

        HomeTerritoryDebug.Snapshot snapshot = HomeTerritoryDebug.inspect(
                Collections.singletonList(claim), current, status);

        assertEquals("Oakvale", snapshot.village);
        assertEquals("Lanfir's Claim", snapshot.claim);
        assertFalse(snapshot.villageHome);
        assertTrue(snapshot.claimHome);
        assertTrue(snapshot.home);
        assertFalse(snapshot.indoorHome);
        assertFalse(snapshot.loading);
        assertFalse(snapshot.navigationLoading);
        assertEquals(2, snapshot.claimTiles);
    }

    @Test
    void describesInheritedHomeWithStableNavigationIdentity() {
        HomeLocationResolver.Status status = indoorAutoStatus(
                "Lanfir's Claim -> Stone Mansion", 701L, 700L);
        HomeTerritoryDebug.Snapshot snapshot = HomeTerritoryDebug.inspect(
                Collections.emptyList(), null, status);

        assertTrue(snapshot.indoorHome);
        assertTrue(snapshot.home);
        assertEquals("Lanfir's Claim -> Stone Mansion", snapshot.homeSource);
        assertEquals(701L, snapshot.gridId);
        assertEquals(700L, snapshot.instanceId);
    }

    @Test
    void emptyDisplayNameStillYieldsOriginHomeSource() {
        HomeLocationResolver.Status status = indoorAutoStatus("", 701L, 700L);
        HomeTerritoryDebug.Snapshot snapshot = HomeTerritoryDebug.inspect(
                Collections.singletonList(new HomeTerritories.Entry(
                        HomeTerritories.Type.CLAIM, "Lanfir",
                        new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
                                Collections.singleton(new ClaimArea.Tile(42L, 7, 9))))),
                null, status);

        assertTrue(snapshot.indoorHome);
        assertTrue(snapshot.home);
        assertFalse(snapshot.homeSource.isEmpty());
        assertEquals("Lanfir's Claim", snapshot.homeSource);
    }

    @Test
    void navigationLoadingDisplaysIndoorHomeUnknownRatherThanYes() {
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                Collections.emptyList(), Collections.emptyList(), null, false,
                HomeInteriorRegistry.empty(), -1L, 0L, false);
        HomeTerritoryDebug.Snapshot snapshot = HomeTerritoryDebug.inspect(
                Collections.emptyList(), null, status);

        assertTrue(snapshot.navigationLoading);
        assertFalse(snapshot.indoorHome);
        assertFalse(snapshot.home);
        assertEquals(-1L, snapshot.gridId);
        assertEquals(0L, snapshot.instanceId);
    }

    @Test
    void describesDirectVillageAndClaimWithoutInternalSourceToken() {
        ClaimArea claimArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 20), Arrays.asList(
                new ClaimArea.Tile(7L, 10, 20),
                new ClaimArea.Tile(7L, 11, 20)));
        HomeTerritories.Entry village = new HomeTerritories.Entry(
                HomeTerritories.Type.VILLAGE, "Oakvale");
        HomeTerritories.Entry claim = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "Lanfir", claimArea);
        List<HomeTerritories.Entry> homes = Arrays.asList(village, claim);
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                homes, homes, claimArea, false,
                HomeInteriorRegistry.empty(), 1L, 1L, true);

        HomeTerritoryDebug.Snapshot snapshot = HomeTerritoryDebug.inspect(homes, homes, status);

        assertTrue(snapshot.villageHome);
        assertTrue(snapshot.claimHome);
        assertTrue(snapshot.home);
        assertEquals("Oakvale", snapshot.village);
        assertEquals("Lanfir's Claim", snapshot.claim);
        assertEquals(HomeLocationResolver.Source.DIRECT_BOTH, snapshot.source);
        assertNotEquals("village+claim", snapshot.homeSource);
        assertFalse(snapshot.homeSource.contains("village+claim"));
    }

    private static HomeLocationResolver.Status indoorAutoStatus(String displayName, long gridId,
            long instanceId) {
        ClaimArea area = new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
                Collections.singleton(new ClaimArea.Tile(42L, 7, 9)));
        List<HomeTerritories.Entry> saved = Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir", area));
        HomeInteriorRegistry.Binding binding = HomeInteriorRegistry.Binding.automatic(
                "auto:" + instanceId, instanceId, Collections.singleton(gridId),
                Collections.singleton(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9,
                        "gfx/terobjs/arch/stonemansion"),
                displayName, 1L);
        return HomeLocationResolver.resolve(saved, Collections.emptyList(), null, false,
                HomeInteriorRegistry.empty().put(binding), gridId, instanceId, true);
    }
}
