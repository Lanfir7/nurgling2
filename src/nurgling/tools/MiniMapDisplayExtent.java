package nurgling.tools;

import haven.Area;

public final class MiniMapDisplayExtent {
    private MiniMapDisplayExtent() {}

    /**
     * {@code invalidateDisplayCache} clears {@code dgext}/{@code display} while leaving
     * {@code dloc} set, so {@code MiniMap.draw} still runs. Skip grid iteration until
     * {@code redisplay} rebuilds the extent.
     */
    public static boolean canIterate(Area dgext, Object display) {
        return dgext != null && display != null;
    }
}
