package nurgling.widgets;

import haven.Coord;
import haven.Coord2d;

import static nurgling.navigation.ChunkNavConfig.CHUNK_SIZE;

final class ChunkNavMapProjection {
    private static final float MIN_ZOOM = 0.1f;
    private static final float MAX_ZOOM = 50.0f;
    private ChunkNavMapProjection() {
    }

    static Coord2d texturePoint(Coord worldBoundsMin, Coord chunkPosition, Coord2d localTile, int chunkSize) {
        int chunkX = chunkPosition.x - worldBoundsMin.x;
        int chunkY = chunkPosition.y - worldBoundsMin.y;
        return new Coord2d(
                chunkX * chunkSize + localTile.x * chunkSize / CHUNK_SIZE,
                chunkY * chunkSize + localTile.y * chunkSize / CHUNK_SIZE);
    }

    static float[] centeredPan(Coord canvasSize, Coord textureSize, float zoom, Coord2d texturePoint) {
        float scale = baseScale(canvasSize, textureSize) * zoom;
        int drawW = (int) (textureSize.x * scale);
        int drawH = (int) (textureSize.y * scale);
        return new float[]{
                (float) (canvasSize.x / 2f - (canvasSize.x - drawW) / 2f - texturePoint.x * scale),
                (float) (canvasSize.y / 2f - (canvasSize.y - drawH) / 2f - texturePoint.y * scale)};
    }

    static Coord2d project(Coord canvasSize, Coord textureSize, float zoom, float panX, float panY,
                           Coord2d texturePoint) {
        float scale = baseScale(canvasSize, textureSize) * zoom;
        int drawW = (int) (textureSize.x * scale);
        int drawH = (int) (textureSize.y * scale);
        int drawX = (int) ((canvasSize.x - drawW) / 2f + panX);
        int drawY = (int) ((canvasSize.y - drawH) / 2f + panY);
        return new Coord2d(drawX + texturePoint.x * scale, drawY + texturePoint.y * scale);
    }

    static float zoomAfterWheel(float zoom, int wheelAmount) {
        return wheelAmount < 0 ? Math.min(MAX_ZOOM, zoom * 1.2f) : Math.max(MIN_ZOOM, zoom / 1.2f);
    }

    private static float baseScale(Coord canvasSize, Coord textureSize) {
        return Math.min((float) canvasSize.x / textureSize.x, (float) canvasSize.y / textureSize.y);
    }
}
