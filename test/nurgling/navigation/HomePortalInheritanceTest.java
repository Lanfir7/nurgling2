package nurgling.navigation;

import nurgling.tools.HomeInteriorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomePortalInheritanceTest {
    @ParameterizedTest
    @EnumSource(value = ChunkPortal.PortalType.class,
            names = {"MINE_ENTRANCE", "MINEHOLE", "LADDER", "CAVEIN", "CAVEOUT"})
    void neverInheritsIntoMineOrCave(ChunkPortal.PortalType type) {
        assertFalse(HomePortalInheritance.canInherit(type, "outside", "mine1", true, false));
        HomePortalInheritance.Change change = HomePortalInheritance.apply(
                HomeInteriorRegistry.empty(), surfaceHome(),
                traversal(type, "outside", "mine1", true, false, 2001L));
        assertFalse(change.changed);
        assertTrue(change.registry.bindings().isEmpty());
    }

    @Test
    void gateDoesNotInherit() {
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.GATE, "outside", "inside", true, false));
    }

    @Test
    void nullPortalTypeDoesNotInherit() {
        assertFalse(HomePortalInheritance.canInherit(null, "outside", "inside", true, false));
    }

    @Test
    void teleportDoesNotInherit() {
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.DOOR, "outside", "inside", true, true));
    }

    @Test
    void unconfirmedTransitionDoesNotInherit() {
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.DOOR, "outside", "inside", false, false));
    }

    @Test
    void destinationOutsideDoesNotInherit() {
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.DOOR, "inside", "outside", true, false));
    }

    @Test
    void destinationMine1DoesNotInherit() {
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.DOOR, "outside", "mine1", true, false));
    }

    @ParameterizedTest
    @EnumSource(value = ChunkPortal.PortalType.class,
            names = {"DOOR", "STAIRS_UP", "STAIRS_DOWN", "CELLAR"})
    void neverInheritsFromMineOrCaveSource(ChunkPortal.PortalType type) {
        assertFalse(HomePortalInheritance.canInherit(type, "mine1", "inside", true, false));
        assertFalse(HomePortalInheritance.canInherit(type, "mine2", "cellar", true, false));
        assertFalse(HomePortalInheritance.canInherit(type, "cave", "inside", true, false));
        HomePortalInheritance.Change change = HomePortalInheritance.apply(
                HomeInteriorRegistry.empty(), surfaceHome(),
                traversal(type, "mine1", "inside", true, false, 1001L));
        assertFalse(change.changed);
        assertTrue(change.registry.bindings().isEmpty());
    }

    @Test
    void marketBuildingDoesNotBecomeHomeWithoutAHomeSource() {
        HomePortalInheritance.Change change = HomePortalInheritance.apply(
                HomeInteriorRegistry.empty(), HomePortalInheritance.SourceContext.notHome(),
                buildingTraversal());
        assertFalse(change.changed);
        assertTrue(change.registry.bindings().isEmpty());
    }

    @Test
    void surfaceHomeDoorCreatesIndoorBinding() {
        assertTrue(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.DOOR, "outside", "inside", true, false));
        HomePortalInheritance.Change change = HomePortalInheritance.apply(
                HomeInteriorRegistry.empty(), surfaceHome(), buildingTraversal());
        assertTrue(change.changed);
        HomeInteriorRegistry.Binding binding = onlyBinding(change.registry);
        assertEquals("auto:" + doorPortal().stableKey(), binding.id);
        assertEquals(setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                binding.origins);
        assertEquals(setOf(1001L), binding.gridIds);
        assertEquals(2L, binding.instanceId);
        assertEquals(doorPortal(), binding.rootPortal);
        assertFalse(binding.manual);
    }

    @Test
    void indoorStairsUpMergesDestinationGridIntoExistingBinding() {
        assertTrue(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.STAIRS_UP, "inside", "inside", true, false));
        HomeInteriorRegistry.Binding indoor = indoorBinding(setOf(1001L));
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty().put(indoor);
        HomePortalInheritance.Change change = HomePortalInheritance.apply(registry,
                new HomePortalInheritance.SourceContext(Collections.<HomeInteriorRegistry.OriginKey>emptySet(),
                        indoor),
                traversal(ChunkPortal.PortalType.STAIRS_UP, "inside", "inside", true, false, 1002L));
        assertTrue(change.changed);
        HomeInteriorRegistry.Binding merged = onlyBinding(change.registry);
        assertEquals(indoor.id, merged.id);
        assertEquals(setOf(1001L, 1002L), merged.gridIds);
        assertEquals(indoor.rootPortal, merged.rootPortal);
        assertEquals(indoor.origins, merged.origins);
    }

    @Test
    void indoorStairsDownMergesDestinationGrid() {
        assertTrue(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.STAIRS_DOWN, "inside", "inside", true, false));
        HomeInteriorRegistry.Binding indoor = indoorBinding(setOf(1001L));
        HomePortalInheritance.Change change = HomePortalInheritance.apply(
                HomeInteriorRegistry.empty().put(indoor),
                new HomePortalInheritance.SourceContext(Collections.<HomeInteriorRegistry.OriginKey>emptySet(),
                        indoor),
                traversal(ChunkPortal.PortalType.STAIRS_DOWN, "inside", "inside", true, false, 1003L));
        assertEquals(setOf(1001L, 1003L), onlyBinding(change.registry).gridIds);
    }

    @Test
    void cellarTraversalMergesCellarGrid() {
        assertTrue(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.CELLAR, "inside", "cellar", true, false));
        HomeInteriorRegistry.Binding indoor = indoorBinding(setOf(1001L));
        HomePortalInheritance.Change change = HomePortalInheritance.apply(
                HomeInteriorRegistry.empty().put(indoor),
                new HomePortalInheritance.SourceContext(Collections.<HomeInteriorRegistry.OriginKey>emptySet(),
                        indoor),
                traversal(ChunkPortal.PortalType.CELLAR, "inside", "cellar", true, false, 1004L));
        assertEquals(setOf(1001L, 1004L), onlyBinding(change.registry).gridIds);
    }

    @Test
    void dualClaimAndVillageSourceRecordsBothOrigins() {
        HomePortalInheritance.SourceContext dual = new HomePortalInheritance.SourceContext(
                setOf(HomeInteriorRegistry.OriginKey.parse("village:oak vale"),
                        HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                null);
        HomePortalInheritance.Change change = HomePortalInheritance.apply(
                HomeInteriorRegistry.empty(), dual, buildingTraversal());
        assertEquals(setOf(HomeInteriorRegistry.OriginKey.parse("village:oak vale"),
                        HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                onlyBinding(change.registry).origins);
    }

    @Test
    void returningInsideToOutsideLeavesSurfaceRegistryUnchanged() {
        HomeInteriorRegistry.Binding indoor = indoorBinding(setOf(1001L));
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty().put(indoor);
        HomePortalInheritance.Change change = HomePortalInheritance.apply(registry,
                new HomePortalInheritance.SourceContext(Collections.<HomeInteriorRegistry.OriginKey>emptySet(),
                        indoor),
                traversal(ChunkPortal.PortalType.DOOR, "inside", "outside", true, false, 100L));
        assertFalse(change.changed);
        assertEquals(registry, change.registry);
        assertEquals(setOf(1001L), onlyBinding(change.registry).gridIds);
        assertFalse(onlyBinding(change.registry).gridIds.contains(100L));
    }

    @Test
    void reenteringSameRootFromSurfaceKeepsMergedFloorAndCellarGrids() {
        HomePortalInheritance.Change first = HomePortalInheritance.apply(
                HomeInteriorRegistry.empty(), surfaceHome(), buildingTraversal());
        HomeInteriorRegistry.Binding indoor = onlyBinding(first.registry);
        HomePortalInheritance.Change floor = HomePortalInheritance.apply(first.registry,
                new HomePortalInheritance.SourceContext(
                        Collections.<HomeInteriorRegistry.OriginKey>emptySet(), indoor),
                traversal(ChunkPortal.PortalType.STAIRS_UP, "inside", "inside", true, false, 1002L));
        HomeInteriorRegistry.Binding withFloor = onlyBinding(floor.registry);
        HomePortalInheritance.Change cellar = HomePortalInheritance.apply(floor.registry,
                new HomePortalInheritance.SourceContext(
                        Collections.<HomeInteriorRegistry.OriginKey>emptySet(), withFloor),
                traversal(ChunkPortal.PortalType.CELLAR, "inside", "cellar", true, false, 1004L));
        HomeInteriorRegistry afterLeave = HomePortalInheritance.apply(cellar.registry,
                new HomePortalInheritance.SourceContext(
                        Collections.<HomeInteriorRegistry.OriginKey>emptySet(),
                        onlyBinding(cellar.registry)),
                traversal(ChunkPortal.PortalType.DOOR, "inside", "outside", true, false, 100L)).registry;
        HomePortalInheritance.Change reenter = HomePortalInheritance.apply(
                afterLeave, surfaceHome(), buildingTraversal());
        HomeInteriorRegistry.Binding restored = onlyBinding(reenter.registry);
        assertEquals(indoor.id, restored.id);
        assertEquals(setOf(1001L, 1002L, 1004L), restored.gridIds);
        assertEquals(indoor.origins, restored.origins);
        assertEquals(indoor.rootPortal, restored.rootPortal);
        assertEquals(indoor.displayName, restored.displayName);
        assertFalse(restored.manual);
    }

    private static HomeInteriorRegistry.PortalIdentity doorPortal() {
        return new HomeInteriorRegistry.PortalIdentity(42L, 7, 9,
                "gfx/terobjs/arch/stonemansion-door");
    }

    private static HomePortalInheritance.SourceContext surfaceHome() {
        return new HomePortalInheritance.SourceContext(
                setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                null);
    }

    private static HomePortalInheritance.Traversal buildingTraversal() {
        return traversal(ChunkPortal.PortalType.DOOR, "outside", "inside", true, false, 1001L);
    }

    private static HomePortalInheritance.Traversal traversal(ChunkPortal.PortalType type,
            String fromLayer, String toLayer, boolean confirmed, boolean teleport, long toGridId) {
        return new HomePortalInheritance.Traversal(
                100L, toGridId, 1L, 2L, fromLayer, toLayer, type, doorPortal(),
                "gfx/terobjs/arch/stonemansion-door", confirmed, teleport, 1234L);
    }

    private static HomeInteriorRegistry.Binding indoorBinding(Set<Long> gridIds) {
        return HomeInteriorRegistry.Binding.automatic(
                "auto:" + doorPortal().stableKey(), 2L, gridIds,
                setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                doorPortal(), "", 1000L);
    }

    private static HomeInteriorRegistry.Binding onlyBinding(HomeInteriorRegistry registry) {
        assertEquals(1, registry.bindings().size());
        return registry.bindings().iterator().next();
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... values) {
        return new LinkedHashSet<T>(Arrays.asList(values));
    }
}
