package nurgling.actions.bots;

import nurgling.tools.NAlias;

/** Which cutting tool to equip for one carcass. */
public final class ButcherKnifePolicy {
    /** Butcher's Cleaver: faster butchering, often lower quality than the best axe. */
    public static final NAlias KNIFE = new NAlias("Butcher's cleaver");
    private static final double QUALITY_SLOP = 0.0001;

    public enum Choice { KNIFE, BEST }

    public static final class Loadout {
        public final boolean knifePresent;
        public final double knifeQuality;
        public final boolean betterTool;

        public Loadout(boolean knifePresent, double knifeQuality, boolean betterTool) {
            this.knifePresent = knifePresent;
            this.knifeQuality = knifeQuality;
            this.betterTool = betterTool;
        }

        /** Knife quality and the best other sharp tool. Null means that tool is absent. */
        public static Loadout of(Double knifeQuality, Double bestOtherQuality) {
            boolean knife = knifeQuality != null && knifeQuality > 0 && Double.isFinite(knifeQuality);
            boolean better = knife && bestOtherQuality != null && Double.isFinite(bestOtherQuality)
                    && bestOtherQuality > knifeQuality;
            return new Loadout(knife, knife ? knifeQuality : 0, better);
        }
    }

    private ButcherKnifePolicy() {}

    /** Missing config keeps the knife path on. An explicit false turns it off. */
    public static boolean useKnifeEnabled(Object configured) {
        return !Boolean.FALSE.equals(configured);
    }

    public static boolean alwaysKnifeEnabled(Object configured) {
        return Boolean.TRUE.equals(configured);
    }

    public static boolean inspectEach(boolean useKnife, boolean alwaysKnife, Loadout loadout) {
        return knifeMode(useKnife, alwaysKnife) && !alwaysKnife
                && loadout != null && loadout.knifePresent && loadout.betterTool;
    }

    public static Choice choose(boolean useKnife, boolean alwaysKnife, Loadout loadout, Double animalQuality) {
        if (!knifeMode(useKnife, alwaysKnife) || loadout == null || !loadout.knifePresent)
            return Choice.BEST;
        if (alwaysKnife || !loadout.betterTool)
            return Choice.KNIFE;
        if (animalQuality == null || !Double.isFinite(animalQuality))
            return Choice.BEST;
        return animalQuality <= loadout.knifeQuality + QUALITY_SLOP ? Choice.KNIFE : Choice.BEST;
    }

    private static boolean knifeMode(boolean useKnife, boolean alwaysKnife) {
        return useKnife || alwaysKnife;
    }
}
