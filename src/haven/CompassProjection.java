package haven;

final class CompassProjection {
    private static final float MIN_W = 0.000001f;

    private CompassProjection() {
    }

    static Coord3f toview(HomoCoord4f clip, Area view) {
        float w = Math.max(Math.abs(clip.w), MIN_W);
        return new HomoCoord4f(clip.x, clip.y, clip.z, w).toview(view);
    }
}
