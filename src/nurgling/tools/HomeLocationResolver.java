package nurgling.tools;

import java.util.Collection;

public final class HomeLocationResolver {
    public enum Source { NONE, DIRECT_VILLAGE, DIRECT_CLAIM, DIRECT_BOTH, INDOOR_AUTO, INDOOR_MANUAL }

    public static final class Status {
        public final boolean villageHome;
        public final boolean claimHome;
        public final boolean indoorHome;
        public final boolean home;
        public final boolean territoryLoading;
        public final boolean navigationLoading;
        public final Source source;
        public final String sourceLabel;
        public final long gridId;
        public final long instanceId;
        public final String bindingId;

        private Status(boolean villageHome, boolean claimHome, boolean indoorHome, boolean home,
                boolean territoryLoading, boolean navigationLoading, Source source,
                String sourceLabel, long gridId, long instanceId, String bindingId) {
            this.villageHome = villageHome;
            this.claimHome = claimHome;
            this.indoorHome = indoorHome;
            this.home = home;
            this.territoryLoading = territoryLoading;
            this.navigationLoading = navigationLoading;
            this.source = source;
            this.sourceLabel = sourceLabel;
            this.gridId = gridId;
            this.instanceId = instanceId;
            this.bindingId = bindingId;
        }
    }

    private HomeLocationResolver() {
    }

    public static Status resolve(Collection<HomeTerritories.Entry> saved,
            Collection<HomeTerritories.Entry> current, ClaimArea currentClaimArea,
            boolean territoryLoading, HomeInteriorRegistry registry,
            long gridId, long instanceId, boolean navigationReady) {
        HomeTerritories.HomeStatus direct = HomeTerritories.status(saved, current, currentClaimArea);
        boolean villageHome = direct.village;
        boolean claimHome = direct.claim;
        HomeInteriorRegistry.Binding indoor = null;
        if (navigationReady && gridId != -1L && instanceId != 0L && registry != null)
            indoor = registry.findActive(gridId, instanceId, saved);
        boolean indoorHome = indoor != null;
        Source source = sourceOf(villageHome, claimHome, indoor);
        return new Status(villageHome, claimHome, indoorHome,
                villageHome || claimHome || indoorHome, territoryLoading, !navigationReady,
                source, sourceLabel(source, indoor, saved), gridId, instanceId,
                indoor != null ? indoor.id : "");
    }

    private static Source sourceOf(boolean villageHome, boolean claimHome,
            HomeInteriorRegistry.Binding indoor) {
        if (villageHome && claimHome)
            return Source.DIRECT_BOTH;
        if (villageHome)
            return Source.DIRECT_VILLAGE;
        if (claimHome)
            return Source.DIRECT_CLAIM;
        if (indoor != null)
            return indoor.manual ? Source.INDOOR_MANUAL : Source.INDOOR_AUTO;
        return Source.NONE;
    }

    private static String sourceLabel(Source source, HomeInteriorRegistry.Binding indoor,
            Collection<HomeTerritories.Entry> saved) {
        switch (source) {
            case DIRECT_VILLAGE:
                return "village";
            case DIRECT_CLAIM:
                return "claim";
            case DIRECT_BOTH:
                return "village+claim";
            case INDOOR_AUTO:
            case INDOOR_MANUAL:
                return indoor != null ? indoor.sourceLabel(saved) : "";
            default:
                return "";
        }
    }
}
