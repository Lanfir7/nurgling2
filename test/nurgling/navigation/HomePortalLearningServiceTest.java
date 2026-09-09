package nurgling.navigation;

import haven.Coord;
import nurgling.tools.ClaimArea;
import nurgling.tools.HomeInteriorRegistry;
import nurgling.tools.HomeTerritories;
import org.junit.jupiter.api.Test;

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

class HomePortalLearningServiceTest {
    @Test
    void confirmedBuildingTraversalPersistsImmediately() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        HomePortalLearningService service = service(store, savedClaimContext());
        HomePortalLearningService.Pending pending = service.capture(
                42L, 7, 9, "gfx/terobjs/arch/thatchedhut", 1L, "outside");

        service.confirm(pending, confirmedInsideTraversal(901L, 8001L));

        assertEquals(1, store.updateCount);
        assertNotNull(store.registry.findActive(901L, 8001L, savedClaim()));
    }

    @Test
    void sourceOriginsAreCapturedBeforeTheTransition() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        RecordingContextSupplier contexts = new RecordingContextSupplier(savedClaimContext());
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, contexts, savedClaim());
        HomePortalLearningService.Pending pending = service.capture(
                42L, 7, 9, "gfx/terobjs/arch/thatchedhut", 1L, "outside");

        assertEquals(1, contexts.calls);
        assertEquals(42L, contexts.lastGridId);
        assertEquals(7, contexts.lastX);
        assertEquals(9, contexts.lastY);

        service.confirm(pending, confirmedInsideTraversal(901L, 8001L));

        assertEquals(1, contexts.calls);
        assertEquals(savedClaimContext().directOrigins,
                store.registry.findActive(901L, 8001L, savedClaim()).origins);
    }

    @Test
    void overlayDisabledTrackingStaysEnabledWhenSurfaceHomeIsConfigured() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        HomePortalLearningService service = service(store, savedClaimContext());

        assertTrue(service.shouldTrack(false));
        assertTrue(service.shouldTrack(true));
    }

    @Test
    void overlayDisabledTrackingStaysEnabledWhenManualIndoorHomeExists() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        store.registry = HomeInteriorRegistry.empty().markManual(8001L,
                Collections.singleton(901L), "Hut");
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, (grid, x, y) -> HomePortalInheritance.SourceContext.notHome(),
                Collections.<HomeTerritories.Entry>emptyList());

        assertTrue(service.shouldTrack(false));
    }

    @Test
    void overlayDisabledTrackingStaysEnabledWhenActiveIndoorHomeExists() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        store.registry = HomeInteriorRegistry.empty().put(automaticHut(901L, 8001L));
        HomePortalLearningService service = service(store, savedClaimContext());

        assertTrue(service.shouldTrack(false));
    }

    @Test
    void overlayOnlyWhenNoSurfaceOrIndoorHomeIsConfigured() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, (grid, x, y) -> HomePortalInheritance.SourceContext.notHome(),
                Collections.<HomeTerritories.Entry>emptyList());

        assertFalse(service.shouldTrack(false));
        assertTrue(service.shouldTrack(true));
    }

    @Test
    void hearthTeleportWritesNothing() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        HomePortalLearningService service = service(store, savedClaimContext());
        HomePortalLearningService.Pending pending = service.capture(
                42L, 7, 9, "gfx/terobjs/arch/thatchedhut", 1L, "outside");

        service.confirm(pending, traversal(ChunkPortal.PortalType.DOOR, "outside", "inside",
                true, true, 901L, 8001L));

        assertEquals(0, store.updateCount);
        assertTrue(store.registry.bindings().isEmpty());
    }

    @Test
    void mineSourceLayerDoesNotPersistEvenForDoorTraversal() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        HomePortalLearningService service = service(store, savedClaimContext());
        HomePortalLearningService.Pending pending = service.capture(
                42L, 7, 9, "gfx/terobjs/arch/thatchedhut", 1L, "mine1");

        service.confirm(pending, traversal(ChunkPortal.PortalType.DOOR, "mine1", "inside",
                true, false, 901L, 8001L));

        assertEquals(0, store.updateCount);
        assertTrue(store.registry.bindings().isEmpty());
    }

    @Test
    void caveSourceLayerDoesNotPersistEvenForStairs() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        HomePortalLearningService service = service(store, savedClaimContext());
        HomePortalLearningService.Pending pending = service.capture(
                42L, 7, 9, "gfx/terobjs/arch/upstairs", 1L, "cave");

        service.confirm(pending, traversal(ChunkPortal.PortalType.STAIRS_UP, "cave", "inside",
                true, false, 901L, 8001L));

        assertEquals(0, store.updateCount);
        assertTrue(store.registry.bindings().isEmpty());
    }

    @Test
    void confirmedTraversalClearsMatchingSuppression() {
        HomeInteriorRegistry.PortalIdentity portal = hutPortal();
        HomeInteriorRegistry.Binding binding = automaticHut(901L, 8001L);
        FakeRegistryAccess store = new FakeRegistryAccess();
        store.registry = HomeInteriorRegistry.empty().put(binding).remove(binding.id, true);
        assertTrue(store.registry.isSuppressed(portal));
        HomePortalLearningService service = service(store, savedClaimContext());
        HomePortalLearningService.Pending pending = service.capture(
                42L, 7, 9, "gfx/terobjs/arch/thatchedhut", 1L, "outside");

        service.confirm(pending, confirmedInsideTraversal(901L, 8001L));

        assertFalse(store.registry.isSuppressed(portal));
        assertNotNull(store.registry.findActive(901L, 8001L, savedClaim()));
    }

    @Test
    void unconfirmedBackfillDoesNotClearSuppressionOrPersist() {
        HomeInteriorRegistry.PortalIdentity portal = hutPortal();
        HomeInteriorRegistry.Binding binding = automaticHut(901L, 8001L);
        FakeRegistryAccess store = new FakeRegistryAccess();
        store.registry = HomeInteriorRegistry.empty().put(binding).remove(binding.id, true);
        HomePortalLearningService service = service(store, savedClaimContext());
        HomePortalLearningService.Pending pending = service.capture(
                42L, 7, 9, "gfx/terobjs/arch/thatchedhut", 1L, "outside");

        service.confirm(pending, traversal(ChunkPortal.PortalType.DOOR, "outside", "inside",
                false, false, 901L, 8001L));

        assertEquals(0, store.updateCount);
        assertTrue(store.registry.isSuppressed(portal));
        assertNull(store.registry.findActive(901L, 8001L, savedClaim()));
    }

    @Test
    void nullLocalCoordDoesNotCaptureOrPersist() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        RecordingContextSupplier contexts = new RecordingContextSupplier(savedClaimContext());
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, contexts, savedClaim());

        HomePortalLearningService.Pending pending = service.capture(
                42L, null, "gfx/terobjs/arch/thatchedhut", 1L, "outside");

        assertNull(pending);
        assertEquals(0, contexts.calls);
        service.confirm(pending, confirmedInsideTraversal(901L, 8001L));
        assertEquals(0, store.updateCount);
        assertTrue(store.registry.bindings().isEmpty());
    }

    @Test
    void unresolvedSourceGridDoesNotCaptureOrPersist() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        RecordingContextSupplier contexts = new RecordingContextSupplier(savedClaimContext());
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, contexts, savedClaim());

        HomePortalLearningService.Pending pending = service.capture(
                -1L, new Coord(7, 9), "gfx/terobjs/arch/thatchedhut", 1L, "outside");

        assertNull(pending);
        assertEquals(0, contexts.calls);
        service.confirm(pending, confirmedInsideTraversal(901L, 8001L));
        assertEquals(0, store.updateCount);
    }

    @Test
    void stalePreviousLocalCoordIsNotReusedForNewUnresolvedPortal() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        RecordingContextSupplier contexts = new RecordingContextSupplier(savedClaimContext());
        HomePortalLearningService service = new HomePortalLearningService(
                "test-world", store, contexts, savedClaim());
        PortalTraversalTracker tracker = new PortalTraversalTracker(null, null, null, service);

        HomePortalLearningService.Pending first = tracker.bindLastActionPortal(
                null, new Coord(7, 9), 42L, "gfx/terobjs/arch/thatchedhut");
        assertNotNull(first);
        assertEquals(7, first.portalCoord.x);
        assertEquals(9, first.portalCoord.y);
        assertEquals(1, contexts.calls);

        HomePortalLearningService.Pending second = tracker.bindLastActionPortal(
                null, null, 99L, "gfx/terobjs/arch/thatchedhut");
        assertNull(second);
        assertEquals(1, contexts.calls);
        service.confirm(second, confirmedInsideTraversal(901L, 8001L));
        assertEquals(0, store.updateCount);
        assertTrue(store.registry.bindings().isEmpty());
    }

    @Test
    void resolvedPortalIdentityStillCaptures() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        HomePortalLearningService service = service(store, savedClaimContext());
        PortalTraversalTracker tracker = new PortalTraversalTracker(null, null, null, service);

        HomePortalLearningService.Pending pending = tracker.bindLastActionPortal(
                null, new Coord(7, 9), 42L, "gfx/terobjs/arch/thatchedhut");

        assertNotNull(pending);
        assertEquals(42L, pending.sourceGridId);
        assertEquals(7, pending.portalCoord.x);
        assertEquals(9, pending.portalCoord.y);
        service.confirm(pending, confirmedInsideTraversal(901L, 8001L));
        assertEquals(1, store.updateCount);
        assertNotNull(store.registry.findActive(901L, 8001L, savedClaim()));
    }

    @Test
    void disabledServiceNeverPersistsAndTracksOnlyOverlay() {
        FakeRegistryAccess store = new FakeRegistryAccess();
        HomePortalLearningService service = HomePortalLearningService.disabled();
        HomePortalLearningService.Pending pending = service.capture(
                42L, 7, 9, "gfx/terobjs/arch/thatchedhut", 1L, "outside");

        service.confirm(pending, confirmedInsideTraversal(901L, 8001L));

        assertEquals(0, store.updateCount);
        assertFalse(service.shouldTrack(false));
        assertTrue(service.shouldTrack(true));
    }

    private static HomePortalLearningService service(FakeRegistryAccess store,
            HomePortalInheritance.SourceContext source) {
        return new HomePortalLearningService("test-world", store, (grid, x, y) -> source, savedClaim());
    }

    private static HomePortalInheritance.SourceContext savedClaimContext() {
        return new HomePortalInheritance.SourceContext(
                setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                null);
    }

    private static List<HomeTerritories.Entry> savedClaim() {
        ClaimArea area = new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
                Collections.singleton(new ClaimArea.Tile(42L, 7, 9)));
        return Collections.singletonList(
                new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir", area));
    }

    private static HomeInteriorRegistry.PortalIdentity hutPortal() {
        return new HomeInteriorRegistry.PortalIdentity(42L, 7, 9, "gfx/terobjs/arch/thatchedhut");
    }

    private static HomeInteriorRegistry.Binding automaticHut(long gridId, long instanceId) {
        return HomeInteriorRegistry.Binding.automatic(
                "auto:" + hutPortal().stableKey(), instanceId, setOf(gridId),
                setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
                hutPortal(), "", 1000L);
    }

    private static HomePortalInheritance.Traversal confirmedInsideTraversal(long toGridId,
            long toInstanceId) {
        return traversal(ChunkPortal.PortalType.DOOR, "outside", "inside", true, false,
                toGridId, toInstanceId);
    }

    private static HomePortalInheritance.Traversal traversal(ChunkPortal.PortalType type,
            String fromLayer, String toLayer, boolean confirmed, boolean teleport,
            long toGridId, long toInstanceId) {
        return new HomePortalInheritance.Traversal(
                42L, toGridId, 1L, toInstanceId, fromLayer, toLayer, type, hutPortal(),
                "gfx/terobjs/arch/thatchedhut-door", confirmed, teleport, 1234L);
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

    private static final class RecordingContextSupplier
            implements HomePortalLearningService.SourceContextSupplier {
        private final HomePortalInheritance.SourceContext source;
        int calls;
        long lastGridId;
        int lastX;
        int lastY;

        RecordingContextSupplier(HomePortalInheritance.SourceContext source) {
            this.source = source;
        }

        @Override
        public HomePortalInheritance.SourceContext capture(long sourceGridId, int portalX, int portalY) {
            calls++;
            lastGridId = sourceGridId;
            lastX = portalX;
            lastY = portalY;
            return source;
        }
    }
}
