package nurgling.navigation;

import haven.Coord;

/**
 * Distinguishes a verified ordinary walk from initial loading or portal arrival.
 */
final class ChunkNavWalkTransitionGate {
    private long previousGridId = -1;
    private ChunkNavMapNeighborRepair.GridRef previousRef;
    private String previousLayer;

    synchronized boolean observe(long playerGridId, ChunkNavMapNeighborRepair.GridRef currentRef,
                                 String currentLayer) {
        if (currentRef == null || currentLayer == null) {
            return false;
        }
        boolean walked = previousGridId != -1 && playerGridId != previousGridId
                && previousRef != null
                && currentLayer.equals(previousLayer)
                && isOrdinaryNeighbor(previousRef, currentRef);
        previousGridId = playerGridId;
        previousRef = currentRef;
        previousLayer = currentLayer;
        return walked;
    }

    static boolean isOrdinaryNeighbor(ChunkNavMapNeighborRepair.GridRef first,
                                      ChunkNavMapNeighborRepair.GridRef second) {
        return first != null && second != null
                && first.gridId != second.gridId
                && first.segmentId == second.segmentId
                && cardinal(first.segmentCoord, second.segmentCoord);
    }

    private static boolean cardinal(Coord first, Coord second) {
        if (first == null || second == null) return false;
        int dx = second.x - first.x;
        int dy = second.y - first.y;
        return (Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1);
    }
}
