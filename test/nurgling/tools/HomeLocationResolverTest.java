package nurgling.tools;

import nurgling.navigation.ChunkNavManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeLocationResolverTest {
    @Test
    void exactStoredGridRestoresHomeAfterRestartInside() {
        HomeInteriorRegistry registry = registryWithAutomaticBinding(
                700L, setOf(701L, 702L), "claim-anchor:42:7:9");
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                registry, 702L, 700L, true);

        assertFalse(status.villageHome);
        assertFalse(status.claimHome);
        assertTrue(status.indoorHome);
        assertTrue(status.home);
        assertEquals(HomeLocationResolver.Source.INDOOR_AUTO, status.source);
    }

    @Test
    void unknownChunkNavIdentityNeverBecomesHome() {
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                registryWithAutomaticBinding(700L, setOf(701L), "claim-anchor:42:7:9"),
                -1L, 0L, false);

        assertFalse(status.indoorHome);
        assertFalse(status.home);
        assertTrue(status.navigationLoading);
    }

    @Test
    void directVillageReportsVillageHome() {
        List<HomeTerritories.Entry> village = Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oak Vale"));
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                village, village, null, false,
                HomeInteriorRegistry.empty(), 1L, 1L, true);

        assertTrue(status.villageHome);
        assertFalse(status.claimHome);
        assertFalse(status.indoorHome);
        assertTrue(status.home);
        assertEquals(HomeLocationResolver.Source.DIRECT_VILLAGE, status.source);
    }

    @Test
    void directClaimReportsClaimHome() {
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), claimArea(), false,
                HomeInteriorRegistry.empty(), 1L, 1L, true);

        assertFalse(status.villageHome);
        assertTrue(status.claimHome);
        assertFalse(status.indoorHome);
        assertTrue(status.home);
        assertEquals(HomeLocationResolver.Source.DIRECT_CLAIM, status.source);
    }

    @Test
    void instanceFallbackRestoresHomeWhenGridIsUnknown() {
        HomeInteriorRegistry registry = registryWithAutomaticBinding(
                700L, setOf(701L), "claim-anchor:42:7:9");
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                registry, 999L, 700L, true);

        assertTrue(status.indoorHome);
        assertTrue(status.home);
        assertEquals(HomeLocationResolver.Source.INDOOR_AUTO, status.source);
    }

    @Test
    void inactiveDeletedOriginDoesNotActivateAutomaticBinding() {
        HomeInteriorRegistry registry = registryWithAutomaticBinding(
                700L, setOf(701L, 702L), "claim-anchor:42:7:9");
        List<HomeTerritories.Entry> unrelated = Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Market"));
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                unrelated, Collections.emptyList(), null, false,
                registry, 702L, 700L, true);

        assertFalse(status.indoorHome);
        assertFalse(status.home);
        assertEquals(HomeLocationResolver.Source.NONE, status.source);
    }

    @Test
    void manualBindingStaysActiveWithoutOrigin() {
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty()
                .markManual(700L, setOf(701L), "Cellar");
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                Collections.emptyList(), Collections.emptyList(), null, false,
                registry, 701L, 700L, true);

        assertTrue(status.indoorHome);
        assertTrue(status.home);
        assertEquals(HomeLocationResolver.Source.INDOOR_MANUAL, status.source);
    }

    @Test
    void directMatchWinsSourceWhileIndoorStillReported() {
        HomeInteriorRegistry registry = registryWithAutomaticBinding(
                700L, setOf(701L), "claim-anchor:42:7:9");
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), claimArea(), false,
                registry, 701L, 700L, true);

        assertTrue(status.claimHome);
        assertTrue(status.indoorHome);
        assertTrue(status.home);
        assertEquals(HomeLocationResolver.Source.DIRECT_CLAIM, status.source);
    }

    @Test
    void navigationNotReadyCannotCreateIndoorMatch() {
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                registryWithAutomaticBinding(700L, setOf(701L), "claim-anchor:42:7:9"),
                701L, 700L, false);

        assertFalse(status.indoorHome);
        assertFalse(status.home);
        assertTrue(status.navigationLoading);
    }

    @Test
    void unknownGridIdCannotCreateIndoorMatch() {
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                registryWithAutomaticBinding(700L, setOf(701L), "claim-anchor:42:7:9"),
                -1L, 700L, true);

        assertFalse(status.indoorHome);
        assertFalse(status.home);
    }

    @Test
    void zeroInstanceIdCannotCreateIndoorMatch() {
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                registryWithAutomaticBinding(700L, setOf(701L), "claim-anchor:42:7:9"),
                701L, 0L, true);

        assertFalse(status.indoorHome);
        assertFalse(status.home);
    }

    @Test
    void surfaceInstanceFallbackDoesNotMatchUnrelatedSurfaceGrid() {
        HomeInteriorRegistry registry = registryWithAutomaticBinding(
                ChunkNavManager.SURFACE_INSTANCE, setOf(1001L), "claim-anchor:42:7:9");
        HomeLocationResolver.Status wilderness = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                registry, 42L, ChunkNavManager.SURFACE_INSTANCE, true);

        assertFalse(wilderness.indoorHome);
        assertFalse(wilderness.home);
        assertEquals(HomeLocationResolver.Source.NONE, wilderness.source);
        assertEquals("", wilderness.bindingId);
    }

    @Test
    void exactGridStillMatchesWhenSessionInstanceIsSurfaceSentinel() {
        HomeInteriorRegistry registry = registryWithAutomaticBinding(
                700L, setOf(1001L), "claim-anchor:42:7:9");
        HomeLocationResolver.Status inside = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                registry, 1001L, ChunkNavManager.SURFACE_INSTANCE, true);

        assertTrue(inside.indoorHome);
        assertTrue(inside.home);
        assertEquals(HomeLocationResolver.Source.INDOOR_AUTO, inside.source);
    }

    @Test
    void emptyDisplayNameFallsBackToActiveOriginNames() {
        HomeInteriorRegistry.Binding binding = HomeInteriorRegistry.Binding.automatic(
                "auto:empty", 700L, setOf(701L),
                setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9,
                        "gfx/terobjs/arch/stonemansion"),
                "", 1L);
        HomeLocationResolver.Status status = HomeLocationResolver.resolve(
                savedClaim(), Collections.emptyList(), null, false,
                HomeInteriorRegistry.empty().put(binding), 701L, 700L, true);

        assertTrue(status.indoorHome);
        assertEquals(HomeLocationResolver.Source.INDOOR_AUTO, status.source);
        assertFalse(status.sourceLabel.isEmpty());
        assertEquals("Lanfir's Claim", status.sourceLabel);
        assertNotEquals("None", status.sourceLabel);
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... values) {
        return new LinkedHashSet<T>(Arrays.asList(values));
    }

    private static ClaimArea claimArea() {
        return new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
                Collections.singleton(new ClaimArea.Tile(42L, 7, 9)));
    }

    private static List<HomeTerritories.Entry> savedClaim() {
        return Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir", claimArea()));
    }

    private static HomeInteriorRegistry registryWithAutomaticBinding(long instanceId,
            Set<Long> gridIds, String origin) {
        HomeInteriorRegistry.Binding binding = HomeInteriorRegistry.Binding.automatic(
                "auto:" + instanceId, instanceId, gridIds,
                setOf(HomeInteriorRegistry.OriginKey.parse(origin)),
                new HomeInteriorRegistry.PortalIdentity(42L, 7, 9,
                        "gfx/terobjs/arch/stonemansion"),
                "auto:" + instanceId, 1L);
        return HomeInteriorRegistry.empty().put(binding);
    }
}
