package nurgling.tools;

/** Shared forward/inverse quality formulas used by Mining Master and planning. */
public final class MiningQuality {
    private MiningQuality() { }

    public static double fromWall(double wallQuality, double toolQuality, double coefficient) {
        if(wallQuality < toolQuality) return wallQuality;
        if(coefficient <= 0) coefficient = 1.0;
        return toolQuality + 0.5 * ((wallQuality - 10.0) - (toolQuality - 10.0) / coefficient);
    }

    public static double wallFromDrop(double dropQuality, double toolQuality, double coefficient) {
        if(dropQuality < toolQuality) return dropQuality;
        if(coefficient <= 0) coefficient = 1.0;
        return ((dropQuality - toolQuality) * 2.0 + (toolQuality - 10.0) / coefficient) + 10.0;
    }
}
