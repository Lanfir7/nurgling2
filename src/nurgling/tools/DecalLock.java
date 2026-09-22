package nurgling.tools;

import haven.*;
import nurgling.NConfig;
import nurgling.NUtils;

/** Makes clicks on parchment decals target their host gob while the option is enabled. */
public class DecalLock {
    public static boolean enabled() {
        Boolean locked = (Boolean) NConfig.get(NConfig.Key.lockDecals);
        return locked != null && locked;
    }

    public static boolean isDecal(Gob.Overlay ol) {
        Sprite spr = ol.spr;
        return spr != null && spr.res != null && CustomizeResLayer.PARCHMENT_DECAL.equals(spr.res.name);
    }

    public static boolean passesThrough(Gob.Overlay ol) {
        return isDecal(ol) && enabled();
    }

    public static Gob.Overlay findDecal(Gob gob) {
        for (Gob.Overlay ol : gob.ols) {
            if (isDecal(ol)) {
                return ol;
            }
        }
        return null;
    }

    /** Sends the usual decal right-click directly, bypassing the click-through lock. */
    public static void takeDecal(Gob gob, Gob.Overlay decal) {
        FastMesh.MeshRes mesh = decal.spr.res.layer(FastMesh.MeshRes.class);
        int meshid = (mesh != null) ? mesh.id : -1;
        Coord rc = gob.rc.floor(OCache.posres);
        NUtils.getGameUI().map.wdgmsg("click", Coord.z, rc, 3, 0, 1, (int) gob.id, rc, decal.id, meshid);
    }
}
