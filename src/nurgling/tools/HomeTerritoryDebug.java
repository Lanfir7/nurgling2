package nurgling.tools;

import java.util.Collection;

/** Builds the current-location snapshot shown by Home Setup diagnostics. */
public final class HomeTerritoryDebug {
    public static final class Snapshot {
        public final String village;
        public final String claim;
        public final boolean villageHome;
        public final boolean claimHome;
        public final boolean home;
        public final boolean loading;
        public final int claimTiles;

        private Snapshot(String village, String claim, boolean villageHome, boolean claimHome,
                         boolean loading, int claimTiles) {
            this.village = village;
            this.claim = claim;
            this.villageHome = villageHome;
            this.claimHome = claimHome;
            this.home = villageHome || claimHome;
            this.loading = loading;
            this.claimTiles = claimTiles;
        }
    }

    private HomeTerritoryDebug() {
    }

    public static Snapshot inspect(Collection<HomeTerritories.Entry> saved,
                                   Collection<HomeTerritories.Entry> current,
                                   ClaimArea currentClaimArea,
                                   boolean loading) {
        String village = "";
        String claim = "";
        if (current != null) {
            for (HomeTerritories.Entry entry : current) {
                if (entry == null)
                    continue;
                if (entry.type == HomeTerritories.Type.VILLAGE && village.isEmpty())
                    village = entry.displayName();
                if (entry.type == HomeTerritories.Type.CLAIM && claim.isEmpty())
                    claim = entry.displayName();
            }
        }
        if (claim.isEmpty() && currentClaimArea != null)
            claim = new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "", currentClaimArea)
                    .displayName();
        HomeTerritories.HomeStatus status = HomeTerritories.status(saved, current, currentClaimArea);
        return new Snapshot(village, claim, status.village, status.claim, loading,
                currentClaimArea == null ? 0 : currentClaimArea.size());
    }
}
