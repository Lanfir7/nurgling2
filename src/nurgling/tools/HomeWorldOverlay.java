package nurgling.tools;

import java.util.Collection;

/**
 * Decides when personal-claim, village, and mine-support colors stay off home land
 * even though their main toggles are on.
 */
public final class HomeWorldOverlay {
    private HomeWorldOverlay() {
    }

    public static boolean enabled(Object configValue) {
        return Boolean.TRUE.equals(configValue);
    }

    /** Missing values keep the overlay, matching the stored default. */
    public static boolean showMiningAtHome(Object configValue) {
        return configValue == null || Boolean.TRUE.equals(configValue);
    }

    public static boolean hidesTerritory(Object hideClaim, Object hideVillage, Collection<String> tags) {
        if (tags == null)
            return false;
        boolean claim = false;
        boolean village = false;
        for (String tag : tags) {
            if ("cplot".equals(tag))
                claim = true;
            else if ("vlg".equals(tag))
                village = true;
        }
        return (claim && enabled(hideClaim)) || (village && enabled(hideVillage));
    }

    /** Own layers drop everywhere in view; a layer without that tag drops only under the player. */
    public static boolean layerCoversHome(Collection<String> tags, boolean coversPlayer) {
        if (tags != null) {
            for (String tag : tags) {
                if ("own".equals(tag))
                    return true;
            }
        }
        return coversPlayer;
    }

    public static boolean suppressTerritoryLayer(Object hideClaim, Object hideVillage, boolean inHomeLand,
                                                 Collection<String> tags, boolean coversPlayer) {
        return hidesTerritory(hideClaim, hideVillage, tags)
                && inHomeLand && layerCoversHome(tags, coversPlayer);
    }

    public static boolean suppressMining(Object showAtHome, boolean inHomeLand) {
        return inHomeLand && !showMiningAtHome(showAtHome);
    }
}
