package nurgling.tools;

import haven.Area;
import haven.Coord;
import haven.Gob;
import haven.Loading;
import haven.MCache;
import haven.Polity;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.widgets.NZergwnd;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads and persists the claim and village currently reported by the server. */
public final class CurrentHomeTerritories {
    private static final class ActiveOverlays {
        final Set<HomeTerritories.Type> homeTypes = new LinkedHashSet<>();
        final Set<HomeTerritories.Type> ownTypes = new LinkedHashSet<>();
        boolean realm;
    }

    public static final class Detection {
        public final List<HomeTerritories.Entry> entries;
        public final boolean loading;
        public final ClaimArea claimArea;

        private Detection(List<HomeTerritories.Entry> entries, boolean loading, ClaimArea claimArea) {
            this.entries = entries;
            this.loading = loading;
            this.claimArea = claimArea;
        }
    }

    private CurrentHomeTerritories() {
    }

    public static Detection detect(NGameUI gui) {
        if (gui == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null)
            return new Detection(Collections.emptyList(), false, null);
        try {
            ActiveOverlays active = activeOverlays(gui);
            if (active.homeTypes.isEmpty())
                return new Detection(Collections.emptyList(), false, null);

            Set<String> villages = new LinkedHashSet<>();
            Set<String> realms = new LinkedHashSet<>();
            collectKnownPolities(gui, villages, realms);
            Collection<String> owners = new ArrayList<>(gui.polowners.values());
            if (active.ownTypes.contains(HomeTerritories.Type.CLAIM))
                owners.add(gui.chrid);
            if (active.ownTypes.contains(HomeTerritories.Type.VILLAGE) && villages.size() == 1)
                owners.add(villages.iterator().next());
            List<HomeTerritories.Entry> saved = HomeTerritories.decodeForWorld(
                    NConfig.get(NConfig.Key.homeTerritories), gui.getGenus());
            List<HomeTerritories.Entry> entries = HomeTerritories.classifyOwners(
                    owners, active.homeTypes, villages, realms, gui.chrid, saved, !active.realm);
            ClaimArea claimArea = null;
            if (active.homeTypes.contains(HomeTerritories.Type.CLAIM)) {
                Gob player = gui.map.player();
                Coord tile = player == null || player.rc == null ? null
                        : player.rc.div(MCache.tilesz).floor();
                claimArea = CurrentClaimArea.capture(gui.map.glob.map, tile);
                if (claimArea == null)
                    return new Detection(Collections.emptyList(), true, null);
                String owner = active.ownTypes.contains(HomeTerritories.Type.CLAIM)
                        ? gui.chrid : null;
                entries = HomeTerritories.withCurrentClaim(entries, saved, claimArea, owner);
            }
            return new Detection(entries, false, claimArea);
        } catch (Loading error) {
            return new Detection(Collections.emptyList(), true, null);
        } catch (RuntimeException error) {
            return new Detection(Collections.emptyList(), false, null);
        }
    }

    private static ActiveOverlays activeOverlays(NGameUI gui) {
        ActiveOverlays result = new ActiveOverlays();
        Gob player = gui.map.player();
        if (player == null || player.rc == null)
            return result;
        Coord tile = player.rc.div(MCache.tilesz).floor();
        Area area = Area.sized(tile, Coord.of(1, 1));
        MCache map = gui.map.glob.map;
        for (MCache.OverlayInfo overlay : map.getols(area)) {
            Collection<String> tags = overlay.tags();
            HomeTerritories.Type type = overlayType(tags);
            boolean realm = tags != null && (tags.contains("realm") || tags.contains("prov"));
            if (type == null && !realm)
                continue;
            boolean[] present = new boolean[1];
            map.getol(overlay, area, present);
            if (!present[0])
                continue;
            if (realm)
                result.realm = true;
            if (type != null) {
                result.homeTypes.add(type);
                if (tags.contains("own"))
                    result.ownTypes.add(type);
            }
        }
        return result;
    }

    private static HomeTerritories.Type overlayType(Collection<String> tags) {
        if (tags == null)
            return null;
        if (tags.contains("cplot"))
            return HomeTerritories.Type.CLAIM;
        if (tags.contains("vlg"))
            return HomeTerritories.Type.VILLAGE;
        return null;
    }

    private static void collectKnownPolities(NGameUI gui, Set<String> villages, Set<String> realms) {
        if (gui.zerg == null)
            return;
        for (NZergwnd.PTab<NZergwnd.Category> tab : gui.zerg.types) {
            for (Polity polity : tab.main.pols) {
                String kind = (tab.main.id + " " + polity.type() + " " + polity.cap)
                        .toLowerCase(Locale.ROOT);
                if (kind.contains("village") || kind.contains("vlg"))
                    villages.add(polity.name);
                if (kind.contains("realm") || kind.contains("rlm"))
                    realms.add(polity.name);
            }
        }
    }

    /** Returns true once complete, non-empty territory data was saved. */
    public static boolean save(NGameUI gui) {
        Detection detection = detect(gui);
        if (detection.loading || detection.entries.isEmpty())
            return false;
        String genus = gui.getGenus();
        NConfig.update(NConfig.Key.homeTerritories, stored -> {
            List<HomeTerritories.Entry> saved = HomeTerritories.decodeForWorld(stored, genus);
            return HomeTerritories.encodeForWorld(stored, genus,
                    HomeTerritories.merge(saved, detection.entries));
        });
        return true;
    }

    /** Independent village/claim checks for storage and workstation consumers. */
    public static HomeTerritories.HomeStatus status(NGameUI gui) {
        Detection detection = detect(gui);
        if (gui == null)
            return HomeTerritories.status(Collections.emptyList(), Collections.emptyList(), null);
        List<HomeTerritories.Entry> saved = HomeTerritories.decodeForWorld(
                NConfig.get(NConfig.Key.homeTerritories), gui.getGenus());
        return HomeTerritories.status(saved, detection.entries, detection.claimArea);
    }

    public static boolean isCurrentVillageHome(NGameUI gui) {
        return status(gui).village;
    }

    public static boolean isCurrentClaimHome(NGameUI gui) {
        return status(gui).claim;
    }
}
