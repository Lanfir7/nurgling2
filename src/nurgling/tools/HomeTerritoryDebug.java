package nurgling.tools;

import java.util.Collection;

/** Builds the current-location snapshot shown by Home Setup diagnostics. */
public final class HomeTerritoryDebug {
    public static final class Snapshot {
        public final String village;
        public final String claim;
        public final boolean villageHome;
        public final boolean claimHome;
        public final boolean indoorHome;
        public final boolean home;
        public final boolean loading;
        public final boolean navigationLoading;
        public final String homeSource;
        public final long gridId;
        public final long instanceId;
        public final HomeLocationResolver.Source source;
        public final int claimTiles;

        private Snapshot(String village, String claim, boolean villageHome, boolean claimHome,
                         boolean indoorHome, boolean home, boolean loading, boolean navigationLoading,
                         String homeSource, long gridId, long instanceId,
                         HomeLocationResolver.Source source, int claimTiles) {
            this.village = village;
            this.claim = claim;
            this.villageHome = villageHome;
            this.claimHome = claimHome;
            this.indoorHome = indoorHome;
            this.home = home;
            this.loading = loading;
            this.navigationLoading = navigationLoading;
            this.homeSource = homeSource;
            this.gridId = gridId;
            this.instanceId = instanceId;
            this.source = source;
            this.claimTiles = claimTiles;
        }
    }

    private HomeTerritoryDebug() {
    }

    public static Snapshot inspect(Collection<HomeTerritories.Entry> saved,
                                   Collection<HomeTerritories.Entry> current,
                                   HomeLocationResolver.Status status) {
        String village = "";
        String claim = "";
        ClaimArea claimArea = null;
        if (current != null) {
            for (HomeTerritories.Entry entry : current) {
                if (entry == null)
                    continue;
                if (entry.type == HomeTerritories.Type.VILLAGE && village.isEmpty())
                    village = entry.displayName();
                if (entry.type == HomeTerritories.Type.CLAIM && claim.isEmpty()) {
                    claim = entry.displayName();
                    claimArea = entry.area;
                }
            }
        }
        HomeLocationResolver.Status resolved = status != null ? status
                : HomeLocationResolver.resolve(saved, current, claimArea, false,
                HomeInteriorRegistry.empty(), -1L, 0L, false);
        return new Snapshot(village, claim, resolved.villageHome, resolved.claimHome,
                resolved.indoorHome, resolved.home, resolved.territoryLoading,
                resolved.navigationLoading, displaySource(resolved), resolved.gridId,
                resolved.instanceId, resolved.source,
                claimArea == null ? 0 : claimArea.size());
    }

    private static String displaySource(HomeLocationResolver.Status status) {
        switch (status.source) {
            case INDOOR_AUTO:
            case INDOOR_MANUAL:
                return status.sourceLabel == null ? "" : status.sourceLabel;
            default:
                return "";
        }
    }
}
