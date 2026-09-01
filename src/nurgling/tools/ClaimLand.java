package nurgling.tools;

import haven.*;
import nurgling.NGameUI;
import nurgling.NUtils;

/**
 * Personal claim (cplot) and village (vlg) tile overlays. Wilderness has neither.
 */
public final class ClaimLand {
    private ClaimLand() {}

    public static boolean isClaimOrVillageTag(String tag) {
        return "cplot".equals(tag) || "vlg".equals(tag);
    }

    /** Icon Settings notify sounds: muted on personal/village claim, unchanged in wilderness. */
    public static boolean shouldPlayIconNotify(boolean onClaim) {
        return !onClaim;
    }

    public static boolean hasClaimOrVillage(Iterable<String> tags) {
        if (tags == null) {
            return false;
        }
        for (String tag : tags) {
            if (isClaimOrVillageTag(tag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOnClaimOrVillage(Gob gob) {
        if (gob == null || gob.rc == null) {
            return false;
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui == null || gui.ui == null || gui.ui.sess == null
                || gui.ui.sess.glob == null || gui.ui.sess.glob.map == null) {
            return false;
        }
        MCache map = gui.ui.sess.glob.map;
        try {
            Coord tc = gob.rc.floor(MCache.tilesz);
            MCache.Grid g = map.getgridt(tc);
            if (g == null || g.ols == null || g.ol == null) {
                return false;
            }
            Coord lc = tc.sub(g.ul);
            if (lc.x < 0 || lc.y < 0 || lc.x >= MCache.cmaps.x || lc.y >= MCache.cmaps.y) {
                return false;
            }
            int tileIndex = lc.x + (lc.y * MCache.cmaps.x);
            int n = Math.min(g.ols.length, g.ol.length);
            for (int i = 0; i < n; i++) {
                if (g.ols[i] == null || g.ol[i] == null) {
                    continue;
                }
                if (tileIndex >= g.ol[i].length || !g.ol[i][tileIndex]) {
                    continue;
                }
                Resource res = g.ols[i].get();
                MCache.ResOverlay olinfo = res.layer(MCache.ResOverlay.class);
                if (olinfo != null && hasClaimOrVillage(olinfo.tags())) {
                    return true;
                }
            }
        } catch (Loading e) {
            return false;
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
