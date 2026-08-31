package nurgling.tools;

import haven.res.lib.tree.TreeScale;

/**
 * Growth percent from the server tree/bush scale, ignoring visual-only
 * resize ({@code TreeScale.scale} after QoL resize) and {@code NTreeDisplayScale}.
 */
public final class TreeGrowth {
    private TreeGrowth() {}

    public static int percent(TreeScale ts, boolean bush) {
        float s = serverScale(ts);
        if (bush) {
            return (int) Math.round(100 * (s - 0.3) / 0.7);
        }
        return (int) Math.round(100 * (s - 0.1) / 0.9);
    }

    public static float serverScale(TreeScale ts) {
        if (ts == null) {
            return 0f;
        }
        return ts.originalScale > 0 ? ts.originalScale : ts.scale;
    }

    /** Overlay is shown when real growth is at least the QoL min threshold, including mature trees. */
    public static boolean shouldDraw(int growthPercent, int minThreshold) {
        return growthPercent >= minThreshold;
    }
}
