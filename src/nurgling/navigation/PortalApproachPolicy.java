package nurgling.navigation;

import haven.Coord2d;

/** Selects portals whose recorded tile is already a known reachable approach point. */
final class PortalApproachPolicy {
    private static final double MAX_RECORDED_DISTANCE_TILES = 5.0;

    private PortalApproachPolicy() {
    }

    static boolean usesRecordedTile(String gobName) {
        return gobName != null && gobName.toLowerCase().endsWith("-door");
    }

    static boolean usesRecordedTile(String gobName, Coord2d recordedPoint,
                                    Coord2d livePortalPoint, double tileSize) {
        return usesRecordedTile(gobName)
                && recordedPoint != null
                && livePortalPoint != null
                && tileSize > 0
                && recordedPoint.dist(livePortalPoint) <= tileSize * MAX_RECORDED_DISTANCE_TILES;
    }
}
