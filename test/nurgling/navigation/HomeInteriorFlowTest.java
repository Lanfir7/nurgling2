package nurgling.navigation;

import haven.Coord;
import nurgling.tools.ClaimArea;
import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.HomeLocationResolver;
import nurgling.tools.HomeTerritories;
import nurgling.tools.HomeTerritoryDebug;
import nurgling.widgets.ChunkHomePresentation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeInteriorFlowTest {
    private static final long SURFACE_GRID = 42L;
    private static final long BUILDING_GRID = 1001L;
    private static final long FLOOR_GRID = 1002L;
    private static final long CELLAR_GRID = 1004L;
    private static final long MINE_GRID = 2001L;
    private static final long BUILDING_INSTANCE = 2L;
    private static final long MINE_INSTANCE = 9L;
    private static final String DOOR_RES = "gfx/terobjs/arch/stonemansion-door";

    @Test
    void savedHomesThroughBuildingFloorsCellarRestartAndIndependentOrigins() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        List<HomeTerritories.Entry> saved = savedClaimAndVillage();
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, fixtureContext(store, saved), saved);
        ChunkNavGraph graph = homeGraph();

        assertTrue(service.shouldTrack(false));
        assertTrue(service.shouldTrack(true));

        HomeLocationResolver.Status surface = resolve(saved, saved, claimArea(),
                store.registry, SURFACE_GRID, ChunkNavManager.SURFACE_INSTANCE);
        assertTrue(surface.villageHome);
        assertTrue(surface.claimHome);
        assertFalse(surface.indoorHome);
        assertTrue(surface.home);
        assertEquals(HomeLocationResolver.Source.DIRECT_BOTH, surface.source);

        service.confirm(service.capture(SURFACE_GRID, 7, 9, DOOR_RES,
                ChunkNavManager.SURFACE_INSTANCE, "outside"),
                traversal(ChunkPortal.PortalType.DOOR, "outside", "inside",
                        true, false, BUILDING_GRID, BUILDING_INSTANCE));

        HomeInteriorRegistry.Binding atEntrance = onlyBinding(store.registry);
        assertEquals("auto:" + doorPortal().stableKey(), atEntrance.id);
        assertEquals(setOf(claimOrigin(), villageOrigin()), atEntrance.origins);
        assertEquals(setOf(BUILDING_GRID), atEntrance.gridIds);
        assertEquals(BUILDING_INSTANCE, atEntrance.instanceId);
        assertFalse(atEntrance.manual);
        HomeLocationResolver.Status inside = resolveIndoor(saved, store.registry, BUILDING_GRID);
        assertFalse(inside.villageHome);
        assertFalse(inside.claimHome);
        assertTrue(inside.indoorHome);
        assertTrue(inside.home);
        assertEquals(atEntrance.id, inside.bindingId);
        assertEquals(HomeLocationResolver.Source.INDOOR_AUTO, inside.source);
        assertSameResolverViews(saved, store.registry, chunk(BUILDING_GRID, BUILDING_INSTANCE, "inside"),
                inside);

        service.confirm(service.capture(BUILDING_GRID, 10, 10, "gfx/terobjs/arch/stairs-up",
                BUILDING_INSTANCE, "inside"),
                traversal(ChunkPortal.PortalType.STAIRS_UP, "inside", "inside",
                        true, false, FLOOR_GRID, BUILDING_INSTANCE));
        HomeInteriorRegistry.Binding onFloor = onlyBinding(store.registry);
        assertEquals(atEntrance.id, onFloor.id);
        assertEquals(setOf(BUILDING_GRID, FLOOR_GRID), onFloor.gridIds);
        assertEquals(BUILDING_INSTANCE, onFloor.instanceId);
        HomeLocationResolver.Status floor = resolveIndoor(saved, store.registry, FLOOR_GRID);
        assertTrue(floor.indoorHome);
        assertEquals(atEntrance.id, floor.bindingId);
        assertEquals(atEntrance.instanceId, floor.instanceId);

        service.confirm(service.capture(BUILDING_GRID, 12, 12, "gfx/terobjs/arch/cellardoor",
                BUILDING_INSTANCE, "inside"),
                traversal(ChunkPortal.PortalType.CELLAR, "inside", "cellar",
                        true, false, CELLAR_GRID, BUILDING_INSTANCE));
        HomeInteriorRegistry.Binding inCellar = onlyBinding(store.registry);
        assertEquals(atEntrance.id, inCellar.id);
        assertEquals(setOf(BUILDING_GRID, FLOOR_GRID, CELLAR_GRID), inCellar.gridIds);
        HomeLocationResolver.Status cellar = resolveIndoor(saved, store.registry, CELLAR_GRID);
        assertTrue(cellar.indoorHome);
        assertEquals(atEntrance.id, cellar.bindingId);
        assertSameResolverViews(saved, store.registry, chunk(CELLAR_GRID, BUILDING_INSTANCE, "cellar"),
                cellar);

        HomeInteriorRegistry afterReturn = HomePortalInheritance.apply(store.registry,
                new HomePortalInheritance.SourceContext(
                        Collections.<HomeInteriorRegistry.OriginKey>emptySet(), inCellar),
                traversal(ChunkPortal.PortalType.DOOR, "cellar", "inside",
                        true, false, BUILDING_GRID, BUILDING_INSTANCE)).registry;
        store.registry = afterReturn;
        assertEquals(atEntrance.id, onlyBinding(store.registry).id);
        assertEquals(setOf(BUILDING_GRID, FLOOR_GRID, CELLAR_GRID), onlyBinding(store.registry).gridIds);

        HomeInteriorRegistry afterExit = HomePortalInheritance.apply(store.registry,
                new HomePortalInheritance.SourceContext(
                        Collections.<HomeInteriorRegistry.OriginKey>emptySet(),
                        onlyBinding(store.registry)),
                traversal(ChunkPortal.PortalType.DOOR, "inside", "outside",
                        true, false, SURFACE_GRID, ChunkNavManager.SURFACE_INSTANCE)).registry;
        assertEquals(store.registry, afterExit);
        store.registry = afterExit;
        assertFalse(onlyBinding(store.registry).gridIds.contains(SURFACE_GRID));
        HomeLocationResolver.Status outside = resolve(saved, saved, claimArea(),
                store.registry, SURFACE_GRID, ChunkNavManager.SURFACE_INSTANCE);
        assertTrue(outside.villageHome);
        assertTrue(outside.claimHome);
        assertFalse(outside.indoorHome);
        assertTrue(outside.home);
        assertEquals(HomeLocationResolver.Source.DIRECT_BOTH, outside.source);
        assertEquals("", outside.bindingId);
        assertEquals(ChunkHomePresentation.Kind.RESTRICTED,
                ChunkHomePresentation.forChunk(
                        chunk(SURFACE_GRID, ChunkNavManager.SURFACE_INSTANCE, "outside"),
                        store.registry, saved).kind);

        HomeLocationResolver.Status restart = resolveIndoor(saved, store.registry, FLOOR_GRID);
        assertFalse(restart.villageHome);
        assertFalse(restart.claimHome);
        assertTrue(restart.indoorHome);
        assertTrue(restart.home);
        assertEquals(FLOOR_GRID, restart.gridId);
        assertEquals(BUILDING_INSTANCE, restart.instanceId);
        assertEquals(atEntrance.id, restart.bindingId);
        assertEquals(HomeLocationResolver.Source.INDOOR_AUTO, restart.source);

        service.confirm(service.capture(SURFACE_GRID, 20, 20, "gfx/terobjs/minehole",
                ChunkNavManager.SURFACE_INSTANCE, "outside"),
                traversal(ChunkPortal.PortalType.MINEHOLE, "outside", "mine1",
                        true, false, MINE_GRID, MINE_INSTANCE));
        service.confirm(service.capture(SURFACE_GRID, 22, 22, "gfx/tiles/ridges/cavein",
                ChunkNavManager.SURFACE_INSTANCE, "outside"),
                traversal(ChunkPortal.PortalType.CAVEIN, "outside", "mine1",
                        true, false, MINE_GRID, MINE_INSTANCE));
        assertFalse(onlyBinding(store.registry).gridIds.contains(MINE_GRID));
        HomeLocationResolver.Status mine = resolve(saved, Collections.emptyList(), null,
                store.registry, MINE_GRID, MINE_INSTANCE);
        assertFalse(mine.indoorHome);
        assertFalse(mine.home);
        assertEquals(ChunkHomePresentation.Kind.RESTRICTED,
                ChunkHomePresentation.forChunk(
                        chunk(MINE_GRID, MINE_INSTANCE, "mine1"), store.registry, saved).kind);

        List<HomeTerritories.Entry> villageOnly = Collections.singletonList(savedVillage());
        HomeLocationResolver.Status villageLeft = resolveIndoor(villageOnly, store.registry, BUILDING_GRID);
        assertTrue(villageLeft.indoorHome);
        assertTrue(onlyBinding(store.registry).active(villageOnly));

        HomeLocationResolver.Status noneLeft = resolveIndoor(
                Collections.<HomeTerritories.Entry>emptyList(), store.registry, BUILDING_GRID);
        assertFalse(noneLeft.indoorHome);
        assertFalse(noneLeft.home);
        assertFalse(onlyBinding(store.registry).active(Collections.<HomeTerritories.Entry>emptyList()));

        HomeInteriorRegistry.Binding concurrent = extraBinding();
        HomeInteriorRegistry afterRemoval = store.registry.put(concurrent)
                .applyRemovals(Collections.singleton(atEntrance.id));
        assertNull(afterRemoval.find(atEntrance.id));
        assertNotNull(afterRemoval.find(concurrent.id));
        assertTrue(afterRemoval.isSuppressed(doorPortal()));
        HomeInteriorRegistry backfilled = service.backfillClaims(graph, saved, afterRemoval);
        assertTrue(backfilled.isSuppressed(doorPortal()));
        assertNull(backfilled.findActive(BUILDING_GRID, BUILDING_INSTANCE, saved));
        assertTrue(service.backfillClaims(graph, villageOnly, HomeInteriorRegistry.empty())
                .bindings().isEmpty());
    }

    @Test
    void firstTimeAlreadyInsideExitReenterDoesNotHomeTheSurface() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        List<HomeTerritories.Entry> saved = savedClaimAndVillage();
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, fixtureContext(store, saved), saved);
        ChunkNavData dest = chunk(BUILDING_GRID, ChunkNavManager.SURFACE_INSTANCE, "inside");

        long assigned = PortalTraversalTracker.destinationInstanceAfterTraversal(
                dest, BUILDING_GRID, DOOR_RES);
        PortalTraversalTracker.stampDestinationInstance(dest, assigned);
        assertEquals(BUILDING_GRID, assigned);
        assertTrue(assigned > ChunkNavManager.SURFACE_INSTANCE);

        service.confirm(service.capture(SURFACE_GRID, 7, 9, DOOR_RES,
                ChunkNavManager.SURFACE_INSTANCE, "outside"),
                traversal(ChunkPortal.PortalType.DOOR, "outside", "inside",
                        true, false, BUILDING_GRID, dest.instanceId));

        HomeInteriorRegistry.Binding learned = onlyBinding(store.registry);
        assertEquals(BUILDING_GRID, learned.instanceId);
        assertTrue(learned.gridIds.contains(BUILDING_GRID));
        assertFalse(learned.gridIds.contains(SURFACE_GRID));

        HomeLocationResolver.Status wilderness = resolve(saved, Collections.emptyList(), null,
                store.registry, 99L, ChunkNavManager.SURFACE_INSTANCE);
        assertFalse(wilderness.indoorHome);
        assertFalse(wilderness.home);

        HomeLocationResolver.Status inside = resolve(saved, Collections.emptyList(), null,
                store.registry, BUILDING_GRID, dest.instanceId);
        assertTrue(inside.indoorHome);
        assertTrue(inside.home);
        assertEquals(learned.id, inside.bindingId);

        HomeLocationResolver.Status restartOnExactGrid = resolve(saved, Collections.emptyList(), null,
                store.registry, BUILDING_GRID, ChunkNavManager.SURFACE_INSTANCE);
        assertTrue(restartOnExactGrid.indoorHome);
    }

    @Test
    void overlayIndependentLearningRejectsGateTeleportAndUnknown() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        List<HomeTerritories.Entry> saved = savedClaimAndVillage();
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, fixtureContext(store, saved), saved);

        assertTrue(service.shouldTrack(false));
        HomePortalLearningService.Pending pending = service.capture(
                SURFACE_GRID, 7, 9, DOOR_RES, ChunkNavManager.SURFACE_INSTANCE, "outside");
        service.confirm(pending, traversal(ChunkPortal.PortalType.DOOR, "outside", "inside",
                true, false, BUILDING_GRID, BUILDING_INSTANCE));
        assertEquals(1, store.registry.bindings().size());

        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.GATE, "outside", "inside", true, false));
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.DOOR, "outside", "inside", true, true));
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.DOOR, "outside", "inside", false, false));
        assertFalse(HomePortalInheritance.canInherit(null, "outside", "inside", true, false));
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.MINEHOLE, "outside", "mine1", true, false));
        assertFalse(HomePortalInheritance.canInherit(
                ChunkPortal.PortalType.CAVEIN, "outside", "mine1", true, false));

        int updates = store.updateCount;
        service.confirm(pending, traversal(ChunkPortal.PortalType.GATE, "outside", "inside",
                true, false, BUILDING_GRID + 10, BUILDING_INSTANCE));
        service.confirm(pending, traversal(ChunkPortal.PortalType.DOOR, "outside", "inside",
                true, true, BUILDING_GRID + 11, BUILDING_INSTANCE));
        service.confirm(pending, traversal(ChunkPortal.PortalType.DOOR, "outside", "inside",
                false, false, BUILDING_GRID + 12, BUILDING_INSTANCE));
        assertEquals(updates, store.updateCount);
        assertEquals(setOf(BUILDING_GRID), onlyBinding(store.registry).gridIds);
    }

    @Test
    void manualHomeRemainsUntilUnmarked() {
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty()
                .markManual(BUILDING_INSTANCE, setOf(BUILDING_GRID, FLOOR_GRID), "Stone Mansion");
        HomeLocationResolver.Status marked = resolveIndoor(
                Collections.<HomeTerritories.Entry>emptyList(), registry, FLOOR_GRID);
        assertTrue(marked.indoorHome);
        assertTrue(marked.home);
        assertEquals(HomeLocationResolver.Source.INDOOR_MANUAL, marked.source);

        HomeInteriorRegistry unmarked = registry.unmarkManual(BUILDING_INSTANCE);
        HomeLocationResolver.Status after = resolveIndoor(
                Collections.<HomeTerritories.Entry>emptyList(), unmarked, FLOOR_GRID);
        assertFalse(after.indoorHome);
        assertFalse(after.home);
        assertTrue(unmarked.bindings().isEmpty());
    }

    @Test
    void chunkNavBinaryFormatIsUnchanged() throws IOException {
        assertEquals(0x434E4156, ChunkNavBinaryFormat.MAGIC);
        assertEquals(2, ChunkNavBinaryFormat.BINARY_VERSION);
        assertEquals(72, ChunkNavBinaryFormat.HEADER_SIZE);

        ChunkNavData original = chunk(BUILDING_GRID, BUILDING_INSTANCE, "inside");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ChunkNavBinaryFormat.writeChunk(original, new DataOutputStream(bytes));
        ChunkNavData restored = ChunkNavBinaryFormat.readChunk(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(BUILDING_GRID, restored.gridId);
        assertEquals(BUILDING_INSTANCE, restored.instanceId);
        assertEquals("inside", restored.layer);
    }

    private static void assertSameResolverViews(List<HomeTerritories.Entry> saved,
            HomeInteriorRegistry registry, ChunkNavData chunk, HomeLocationResolver.Status status) {
        HomeTerritoryDebug.Snapshot debug = HomeTerritoryDebug.inspect(
                saved, Collections.<HomeTerritories.Entry>emptyList(), status);
        ChunkHomePresentation presentation = ChunkHomePresentation.forChunk(chunk, registry, saved);
        assertEquals(status.indoorHome, debug.indoorHome);
        assertEquals(status.home, debug.home);
        assertEquals(status.source, debug.source);
        assertEquals(status.gridId, debug.gridId);
        assertEquals(status.instanceId, debug.instanceId);
        assertEquals(status.indoorHome, presentation.active);
        assertEquals(ChunkHomePresentation.Kind.AUTO, presentation.kind);
    }

    private static HomeLocationResolver.Status resolveIndoor(List<HomeTerritories.Entry> saved,
            HomeInteriorRegistry registry, long gridId) {
        return resolve(saved, Collections.<HomeTerritories.Entry>emptyList(), null,
                registry, gridId, BUILDING_INSTANCE);
    }

    private static HomeLocationResolver.Status resolve(List<HomeTerritories.Entry> saved,
            List<HomeTerritories.Entry> current, ClaimArea claimArea, HomeInteriorRegistry registry,
            long gridId, long instanceId) {
        return HomeLocationResolver.resolve(saved, current, claimArea, false,
                registry, gridId, instanceId, true);
    }

    private static HomePortalLearningService.SourceContextSupplier fixtureContext(
            FakeRegistryAccess store, List<HomeTerritories.Entry> saved) {
        return (grid, x, y) -> {
            HomeInteriorRegistry.Binding inherited = store.registry.findActive(grid, 0L, saved);
            LinkedHashSet<HomeInteriorRegistry.OriginKey> origins =
                    new LinkedHashSet<HomeInteriorRegistry.OriginKey>();
            if (inherited == null) {
                for (HomeTerritories.Entry entry : saved)
                    origins.add(HomeInteriorRegistry.OriginKey.from(entry));
            }
            return new HomePortalInheritance.SourceContext(origins, inherited);
        };
    }

    private static ChunkNavGraph homeGraph() {
        ChunkNavGraph graph = new ChunkNavGraph();
        ChunkNavData surface = chunk(SURFACE_GRID, ChunkNavManager.SURFACE_INSTANCE, "outside");
        ChunkPortal door = new ChunkPortal("door", DOOR_RES, ChunkPortal.PortalType.DOOR, new Coord(7, 9));
        door.connectsToGridId = BUILDING_GRID;
        surface.portals.add(door);
        ChunkPortal mine = new ChunkPortal("mine", "gfx/terobjs/minehole",
                ChunkPortal.PortalType.MINEHOLE, new Coord(20, 20));
        mine.connectsToGridId = MINE_GRID;
        surface.portals.add(mine);
        graph.addChunk(surface);
        graph.addChunk(chunk(BUILDING_GRID, BUILDING_INSTANCE, "inside"));
        graph.addChunk(chunk(FLOOR_GRID, BUILDING_INSTANCE, "inside"));
        graph.addChunk(chunk(CELLAR_GRID, BUILDING_INSTANCE, "cellar"));
        graph.addChunk(chunk(MINE_GRID, MINE_INSTANCE, "mine1"));
        return graph;
    }

    private static ChunkNavData chunk(long gridId, long instanceId, String layer) {
        ChunkNavData chunk = new ChunkNavData(gridId);
        chunk.instanceId = instanceId;
        chunk.layer = layer;
        return chunk;
    }

    private static HomePortalInheritance.Traversal traversal(ChunkPortal.PortalType type,
            String fromLayer, String toLayer, boolean confirmed, boolean teleport,
            long toGridId, long toInstanceId) {
        return new HomePortalInheritance.Traversal(
                SURFACE_GRID, toGridId, ChunkNavManager.SURFACE_INSTANCE, toInstanceId,
                fromLayer, toLayer, type, doorPortal(), DOOR_RES, confirmed, teleport, 1234L);
    }

    private static HomeInteriorRegistry.PortalIdentity doorPortal() {
        return new HomeInteriorRegistry.PortalIdentity(SURFACE_GRID, 7, 9, DOOR_RES);
    }

    private static HomeInteriorRegistry.OriginKey claimOrigin() {
        return HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9");
    }

    private static HomeInteriorRegistry.OriginKey villageOrigin() {
        return HomeInteriorRegistry.OriginKey.parse("village:oak vale");
    }

    private static ClaimArea claimArea() {
        return new ClaimArea(new ClaimArea.Tile(SURFACE_GRID, 7, 9),
                Collections.singleton(new ClaimArea.Tile(SURFACE_GRID, 7, 9)));
    }

    private static HomeTerritories.Entry savedClaim() {
        return new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir", claimArea());
    }

    private static HomeTerritories.Entry savedVillage() {
        return new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, "Oak Vale");
    }

    private static List<HomeTerritories.Entry> savedClaimAndVillage() {
        return Arrays.asList(savedClaim(), savedVillage());
    }

    private static HomeInteriorRegistry.Binding extraBinding() {
        return HomeInteriorRegistry.Binding.automatic(
                "auto:concurrent", 8L, setOf(3001L),
                setOf(claimOrigin()),
                new HomeInteriorRegistry.PortalIdentity(99L, 1, 1, "gfx/terobjs/arch/logcabin-door"),
                "concurrent", 1L);
    }

    private static HomeInteriorRegistry.Binding onlyBinding(HomeInteriorRegistry registry) {
        assertEquals(1, registry.bindings().size());
        return registry.bindings().iterator().next();
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... values) {
        return new LinkedHashSet<T>(Arrays.asList(values));
    }

    private static final class FakeRegistryAccess implements HomePortalLearningService.RegistryAccess {
        HomeInteriorRegistry registry = HomeInteriorRegistry.empty();
        int updateCount;

        @Override
        public HomeInteriorRegistry load(String genus) {
            return registry;
        }

        @Override
        public HomeInteriorRegistry update(String genus,
                UnaryOperator<HomeInteriorRegistry> updater) {
            updateCount++;
            registry = updater.apply(registry);
            return registry;
        }
    }
}
