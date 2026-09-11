package nurgling.navigation;

import haven.Coord;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkNavMapNeighborRepairTest {
    @Test
    void surfaceMapNeighborsMergeMismatchedComponentsToSurface() {
        ChunkNavGraph graph = graph(chunk(10, 1, "outside"), chunk(11, 42, "outside"),
                chunk(12, 1, "outside"));

        repair(graph, refs(ref(10, 7, 20, 30), ref(11, 7, 21, 30), ref(12, 7, 22, 30)), 1, 10);

        ChunkNavData west = graph.getChunk(10);
        ChunkNavData east = graph.getChunk(11);
        ChunkNavData fartherEast = graph.getChunk(12);
        assertEquals(ChunkNavManager.SURFACE_INSTANCE, west.instanceId);
        assertEquals(ChunkNavManager.SURFACE_INSTANCE, east.instanceId);
        assertEquals(ChunkNavManager.SURFACE_INSTANCE, fartherEast.instanceId);
        assertEquals(11, west.neighborEast);
        assertEquals(10, east.neighborWest);
        assertEquals(12, east.neighborEast);
        assertEquals(11, fartherEast.neighborWest);
        assertTrue(west.connectedChunks.contains(11L));
        assertTrue(east.connectedChunks.contains(10L));
        assertTrue(east.connectedChunks.contains(12L));
    }

    @Test
    void anchoredCaveOutsideLayerNeighborsMergeToCurrentCaveInstance() {
        ChunkNavGraph graph = graph(chunk(20, 80, "outside"), chunk(21, 81, "outside"));

        repairWithContext(graph, refs(ref(20, 9, 4, 4), ref(21, 9, 4, 5)), 80, true, 20);

        assertEquals(80, graph.getChunk(20).instanceId);
        assertEquals(80, graph.getChunk(21).instanceId);
        assertEquals(21, graph.getChunk(20).neighborSouth);
        assertEquals(20, graph.getChunk(21).neighborNorth);
        assertTrue(graph.getChunk(20).connectedChunks.contains(21L));
    }

    @Test
    void surfacePlayerComponentRepairsWithoutRestampingConcurrentCaveComponent() {
        ChunkNavGraph graph = graph(chunk(70, 1, "outside"), chunk(71, 44, "outside"),
                chunk(80, 80, "outside"), chunk(81, 81, "outside"));
        Map<Long, ChunkNavMapNeighborRepair.GridRef> mapRefs = refs(
                ref(70, 1, 0, 0), ref(71, 1, 1, 0),
                ref(80, 2, 0, 0), ref(81, 2, 1, 0));

        repairWithContext(graph, mapRefs, 1, true, 70);

        assertEquals(1, graph.getChunk(70).instanceId);
        assertEquals(1, graph.getChunk(71).instanceId);
        assertEquals(80, graph.getChunk(80).instanceId);
        assertEquals(81, graph.getChunk(81).instanceId);
        assertEquals(-1, graph.getChunk(80).neighborEast);
    }

    @Test
    void repeatedRepairOfCorrectPlayerComponentDoesNotUpdateTimestamps() {
        ChunkNavGraph graph = graph(chunk(90, 1, "outside"), chunk(91, 2, "outside"));
        Map<Long, ChunkNavMapNeighborRepair.GridRef> mapRefs = refs(ref(90, 1, 0, 0), ref(91, 1, 1, 0));

        repairWithContext(graph, mapRefs, 1, true, 90);
        graph.getChunk(90).lastUpdated = 101;
        graph.getChunk(91).lastUpdated = 102;
        repairWithContext(graph, mapRefs, 1, true, 90);

        assertEquals(101, graph.getChunk(90).lastUpdated);
        assertEquals(102, graph.getChunk(91).lastUpdated);
    }

    @Test
    void firstPortalLikeArrivalDoesNotRestampCaveComponentToSurface() {
        ChunkNavGraph graph = graph(chunk(100, 80, "outside"), chunk(101, 81, "outside"));
        Map<Long, ChunkNavMapNeighborRepair.GridRef> mapRefs = refs(ref(100, 2, 0, 0), ref(101, 2, 1, 0));
        ChunkNavWalkTransitionGate gate = new ChunkNavWalkTransitionGate();

        repairAfterWalk(graph, mapRefs, gate, 1, 100);

        assertEquals(80, graph.getChunk(100).instanceId);
        assertEquals(81, graph.getChunk(101).instanceId);
        assertEquals(-1, graph.getChunk(100).neighborEast);
    }

    @Test
    void repeatedObservationOfSamePlayerGridDoesNotRepair() {
        ChunkNavWalkTransitionGate gate = new ChunkNavWalkTransitionGate();
        ChunkNavMapNeighborRepair.GridRef ref = ref(110, 3, 5, 5);

        assertFalse(gate.observe(110, ref, "outside"));
        assertFalse(gate.observe(110, ref, "outside"));
    }

    @Test
    void unavailableMapReferenceDoesNotConsumePendingBoundaryCrossing() {
        ChunkNavWalkTransitionGate gate = new ChunkNavWalkTransitionGate();
        ChunkNavMapNeighborRepair.GridRef west = ref(111, 3, 5, 5);
        ChunkNavMapNeighborRepair.GridRef east = ref(112, 3, 6, 5);

        assertFalse(gate.observe(111, west, "outside"));
        assertFalse(gate.observe(112, null, "outside"));
        assertTrue(gate.observe(112, east, "outside"));
    }

    @Test
    void onlyCardinalNeighborsInSameMapSegmentAreOrdinaryTransitions() {
        assertTrue(ChunkNavWalkTransitionGate.isOrdinaryNeighbor(
                ref(113, 3, 5, 5), ref(114, 3, 6, 5)));
        assertFalse(ChunkNavWalkTransitionGate.isOrdinaryNeighbor(
                ref(113, 3, 5, 5), ref(114, 4, 6, 5)));
        assertFalse(ChunkNavWalkTransitionGate.isOrdinaryNeighbor(
                ref(113, 3, 5, 5), ref(114, 3, 7, 5)));
    }

    @Test
    void confirmedAdjacentPlayerWalkRepairsComponent() {
        ChunkNavGraph graph = graph(chunk(120, 1, "outside"), chunk(121, 44, "outside"),
                chunk(122, 1, "outside"));
        Map<Long, ChunkNavMapNeighborRepair.GridRef> mapRefs = refs(
                ref(120, 4, 0, 0), ref(121, 4, 1, 0), ref(122, 4, 2, 0));
        ChunkNavWalkTransitionGate gate = new ChunkNavWalkTransitionGate();

        repairAfterWalk(graph, mapRefs, gate, 1, 120);
        repairAfterWalk(graph, mapRefs, gate, 1, 121);

        assertEquals(1, graph.getChunk(120).instanceId);
        assertEquals(1, graph.getChunk(121).instanceId);
        assertEquals(121, graph.getChunk(120).neighborEast);
        assertEquals(120, graph.getChunk(121).neighborWest);
    }

    @Test
    void differentMapSegmentsDoNotMerge() {
        ChunkNavGraph graph = graph(chunk(30, 1, "outside"), chunk(31, 2, "outside"));

        repair(graph, refs(ref(30, 1, 0, 0), ref(31, 2, 1, 0)), 1, 30);

        assertEquals(1, graph.getChunk(30).instanceId);
        assertEquals(2, graph.getChunk(31).instanceId);
        assertEquals(-1, graph.getChunk(30).neighborEast);
        assertFalse(graph.getChunk(30).connectedChunks.contains(31L));
    }

    @Test
    void nonadjacentMapCoordinatesDoNotMerge() {
        ChunkNavGraph graph = graph(chunk(40, 1, "outside"), chunk(41, 2, "outside"));

        repair(graph, refs(ref(40, 1, 0, 0), ref(41, 1, 2, 0)), 1, 40);

        assertEquals(1, graph.getChunk(40).instanceId);
        assertEquals(2, graph.getChunk(41).instanceId);
        assertEquals(-1, graph.getChunk(40).neighborEast);
    }

    @Test
    void differentLayersDoNotMerge() {
        ChunkNavGraph graph = graph(chunk(50, 1, "outside"), chunk(51, 2, "inside"));

        repair(graph, refs(ref(50, 1, 0, 0), ref(51, 1, 1, 0)), 1, 50);

        assertEquals(1, graph.getChunk(50).instanceId);
        assertEquals(2, graph.getChunk(51).instanceId);
        assertEquals(-1, graph.getChunk(50).neighborEast);
    }

    @Test
    void unanchoredCaveAmbiguityDoesNotMerge() {
        ChunkNavGraph graph = graph(chunk(60, 90, "outside"), chunk(61, 91, "outside"));

        repair(graph, refs(ref(60, 3, 0, 0), ref(61, 3, 1, 0)), 92, 60);

        assertEquals(90, graph.getChunk(60).instanceId);
        assertEquals(91, graph.getChunk(61).instanceId);
        assertEquals(-1, graph.getChunk(60).neighborEast);
        assertFalse(graph.getChunk(60).connectedChunks.contains(61L));
    }

    @Test
    void staleSurfaceManagerDoesNotRelabelUnanchoredCaveComponent() {
        ChunkNavGraph graph = graph(chunk(62, 90, "outside"), chunk(63, 91, "outside"));

        repair(graph, refs(ref(62, 4, 0, 0), ref(63, 4, 1, 0)),
                ChunkNavManager.SURFACE_INSTANCE, 62);

        assertEquals(90, graph.getChunk(62).instanceId);
        assertEquals(91, graph.getChunk(63).instanceId);
        assertEquals(-1, graph.getChunk(62).neighborEast);
        assertFalse(graph.getChunk(62).connectedChunks.contains(63L));
    }

    @Test
    void surfaceMajorityWinsFromEitherCrossingDirection() {
        assertSurfaceMajority(200);
        assertSurfaceMajority(202);
    }

    @Test
    void caveMajorityClaimsUnknownChunkDespiteStaleSurfaceContext() {
        ChunkNavGraph graph = graph(chunk(210, 80, "outside"), chunk(211, 80, "outside"),
                chunk(212, 0, "outside"));
        Map<Long, ChunkNavMapNeighborRepair.GridRef> mapRefs = refs(
                ref(210, 8, 0, 0), ref(211, 8, 1, 0), ref(212, 8, 2, 0));

        repairWithContext(graph, mapRefs, 1, false, 212);

        assertEquals(80, graph.getChunk(210).instanceId);
        assertEquals(80, graph.getChunk(211).instanceId);
        assertEquals(80, graph.getChunk(212).instanceId);
    }

    @Test
    void unconfirmedTieDoesNotRelabel() {
        ChunkNavGraph graph = graph(chunk(220, 80, "outside"), chunk(221, 81, "outside"));

        repairWithContext(graph, refs(ref(220, 9, 0, 0), ref(221, 9, 1, 0)), 1, false, 220);

        assertEquals(80, graph.getChunk(220).instanceId);
        assertEquals(81, graph.getChunk(221).instanceId);
        assertEquals(-1, graph.getChunk(220).neighborEast);
    }

    @Test
    void confirmedCaveContextResolvesTie() {
        ChunkNavGraph graph = graph(chunk(230, 80, "outside"), chunk(231, 81, "outside"));

        repairWithContext(graph, refs(ref(230, 10, 0, 0), ref(231, 10, 1, 0)), 80, true, 230);

        assertEquals(80, graph.getChunk(230).instanceId);
        assertEquals(80, graph.getChunk(231).instanceId);
    }

    @Test
    void confirmedCaveContextOverridesStaleSurfaceMajority() {
        ChunkNavGraph graph = graph(chunk(232, 1, "outside"), chunk(233, 1, "outside"),
                chunk(234, 80, "outside"));

        repairWithContext(graph,
                refs(ref(232, 10, 0, 0), ref(233, 10, 1, 0), ref(234, 10, 2, 0)),
                80, true, 234);

        assertEquals(80, graph.getChunk(232).instanceId);
        assertEquals(80, graph.getChunk(233).instanceId);
        assertEquals(80, graph.getChunk(234).instanceId);
    }

    @Test
    void allZeroUnconfirmedComponentRestoresTopologyWithoutRelabeling() {
        ChunkNavGraph graph = graph(chunk(240, 0, "outside"), chunk(241, 0, "outside"));

        repairWithContext(graph, refs(ref(240, 11, 0, 0), ref(241, 11, 1, 0)), 1, false, 240);

        assertEquals(0, graph.getChunk(240).instanceId);
        assertEquals(0, graph.getChunk(241).instanceId);
        assertEquals(241, graph.getChunk(240).neighborEast);
    }

    @Test
    void verifiedReciprocalTopologyAllowsWalkingWhileInstanceIsUnknown() {
        ChunkNavGraph graph = graph(chunk(242, 0, "outside"), chunk(243, 0, "outside"));

        repairWithContext(graph, refs(ref(242, 11, 0, 0), ref(243, 11, 1, 0)), 1, false, 242);

        assertTrue(UnifiedTilePathfinder.canWalkDirectlyBetween(
                graph.getChunk(242), graph.getChunk(243)));
    }

    @Test
    void unknownInstanceWithoutReciprocalTopologyCannotBeWalked() {
        ChunkNavData first = chunk(244, 0, "outside");
        ChunkNavData second = chunk(245, 0, "outside");
        first.connectedChunks.add(second.gridId);

        assertFalse(UnifiedTilePathfinder.canWalkDirectlyBetween(first, second));
    }

    @Test
    void differentKnownInstancesCannotBeWalkedDespiteStaleTopology() {
        ChunkNavData first = chunk(246, 80, "outside");
        ChunkNavData second = chunk(247, 81, "outside");
        first.connectedChunks.add(second.gridId);
        second.connectedChunks.add(first.gridId);

        assertFalse(UnifiedTilePathfinder.canWalkDirectlyBetween(first, second));
    }

    @Test
    void knownAndUnknownInstancesCannotBeWalkedDespiteReciprocalTopology() {
        ChunkNavData known = chunk(248, 80, "outside");
        ChunkNavData unknown = chunk(249, 0, "outside");
        known.neighborEast = unknown.gridId;
        unknown.neighborWest = known.gridId;
        known.connectedChunks.add(unknown.gridId);
        unknown.connectedChunks.add(known.gridId);

        assertFalse(UnifiedTilePathfinder.canWalkDirectlyBetween(known, unknown));
    }

    @Test
    void invalidatingManagerContextPreservesIdButRemovesItsAuthority() {
        ChunkNavManager manager = new ChunkNavManager();
        manager.setCurrentInstanceId(80);

        manager.invalidateCurrentInstanceConfirmation();

        ChunkNavManager.InstanceContext context = manager.getInstanceContext();
        assertEquals(80, context.instanceId);
        assertFalse(context.confirmed);
    }

    @Test
    void genericRecordingDoesNotStampUnknownChunkBeforeTransitionIsClassified() {
        ChunkNavManager.InstanceContext unconfirmed =
                new ChunkNavManager.InstanceContext(ChunkNavManager.SURFACE_INSTANCE, false);
        ChunkNavManager.InstanceContext confirmed =
                new ChunkNavManager.InstanceContext(80, true);

        assertEquals(0, ChunkNavRecorder.instanceForRecording(0, unconfirmed));
        assertEquals(0, ChunkNavRecorder.instanceForRecording(0, confirmed));
        assertEquals(90, ChunkNavRecorder.instanceForRecording(90, unconfirmed));
    }

    private static void repair(ChunkNavGraph graph, Map<Long, ChunkNavMapNeighborRepair.GridRef> refs,
                               long currentInstance, long playerGridId) {
        ChunkNavMapNeighborRepair.repair(graph, refs::get, currentInstance, playerGridId);
    }

    private static void repairAfterWalk(ChunkNavGraph graph, Map<Long, ChunkNavMapNeighborRepair.GridRef> refs,
                                        ChunkNavWalkTransitionGate gate, long currentInstance, long playerGridId) {
        ChunkNavData player = graph.getChunk(playerGridId);
        if (gate.observe(playerGridId, refs.get(playerGridId), player.layer)) {
            repair(graph, refs, currentInstance, playerGridId);
        }
    }

    private static ChunkNavMapNeighborRepair.RepairResult repairWithContext(ChunkNavGraph graph,
            Map<Long, ChunkNavMapNeighborRepair.GridRef> refs, long currentInstance, boolean confirmed,
            long playerGridId) {
        return ChunkNavMapNeighborRepair.repair(graph, refs::get, currentInstance, confirmed,
                playerGridId);
    }

    private static void assertSurfaceMajority(long playerGridId) {
        ChunkNavGraph graph = graph(chunk(200, 1, "outside"), chunk(201, 1, "outside"),
                chunk(202, 42, "outside"));
        Map<Long, ChunkNavMapNeighborRepair.GridRef> mapRefs = refs(
                ref(200, 7, 0, 0), ref(201, 7, 1, 0), ref(202, 7, 2, 0));

        repairWithContext(graph, mapRefs, 42, false, playerGridId);

        assertEquals(1, graph.getChunk(200).instanceId);
        assertEquals(1, graph.getChunk(201).instanceId);
        assertEquals(1, graph.getChunk(202).instanceId);
    }

    private static ChunkNavGraph graph(ChunkNavData... chunks) {
        ChunkNavGraph graph = new ChunkNavGraph();
        for (ChunkNavData chunk : chunks) {
            graph.addChunk(chunk);
        }
        return graph;
    }

    private static ChunkNavData chunk(long id, long instance, String layer) {
        ChunkNavData chunk = new ChunkNavData(id);
        chunk.instanceId = instance;
        chunk.layer = layer;
        return chunk;
    }

    private static ChunkNavMapNeighborRepair.GridRef ref(long id, long seg, int x, int y) {
        return new ChunkNavMapNeighborRepair.GridRef(id, seg, Coord.of(x, y));
    }

    private static Map<Long, ChunkNavMapNeighborRepair.GridRef> refs(ChunkNavMapNeighborRepair.GridRef... refs) {
        Map<Long, ChunkNavMapNeighborRepair.GridRef> byId = new HashMap<>();
        for (ChunkNavMapNeighborRepair.GridRef ref : refs) {
            byId.put(ref.gridId, ref);
        }
        return byId;
    }
}
