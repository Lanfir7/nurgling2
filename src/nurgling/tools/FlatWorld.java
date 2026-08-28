package nurgling.tools;

import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.sessions.SessionContext;
import nurgling.sessions.SessionManager;
import haven.Coord3f;
import haven.Matrix4f;
import haven.render.Homo3D;
import haven.render.Location;
import haven.render.Pipe;

/**
 * The single authority on the flat-world terrain toggle.
 *
 * <p>Flatness is not baked into the map data. Every consumer - the cut meshes built by
 * {@link haven.MapMesh}, the height lookups in {@link haven.MCache}, ridge and flavour
 * geometry - reads {@link NConfig.Key#flatsurface} while it builds. Flipping the key therefore
 * only needs the geometry that was built under the old value thrown away, which is what
 * {@link #apply} does. The setting used to demand a client restart purely because nothing ever
 * asked for that rebuild.
 */
public class FlatWorld {
    private FlatWorld() {}

    public static boolean isEnabled() {
        Object v = NConfig.get(NConfig.Key.flatsurface);
        return (v instanceof Boolean) && (Boolean) v;
    }

    /**
     * Visual ground height for drawing. {@link haven.MCache#getcz} stays the real
     * heightmap (survey math, pathfinding); overlays that sit on the drawn terrain
     * have to go through here or they drape over hills the mesh no longer has.
     */
    public static double visualCz(boolean flat, double realCz) {
        return flat ? 0.0 : realCz;
    }

    public static double visualCz(double realCz) {
        return visualCz(isEnabled(), realCz);
    }

    /**
     * Overlay vertex Z relative to an origin that already sits on visual ground.
     * On a hidden slope {@code pointCz - originCz} buries the downhill side; when
     * the world is drawn flat the overlay stays on the visual plane.
     */
    public static float overlayRelZ(boolean flat, double pointCz, double originCz) {
        return flat ? 0f : (float)(pointCz - originCz);
    }

    public static float overlayRelZ(double pointCz, double originCz) {
        return overlayRelZ(isEnabled(), pointCz, originCz);
    }

    /**
     * Object-space Z for a gob-parented billboard (harvest pillars, labels).
     * {@code obj2view} applies the gob location, whose translation still carries the
     * real heightmap; undo that so the sprite sits {@code localZ} above the visual plane.
     * {@code m10}/{@code m14} are the location matrix's Z-row scale and translation
     * ({@code Matrix4f.get(2,2)} / {@code get(3,2)}).
     */
    public static float flattenBillboardLocalZ(boolean flat, float localZ, float m10, float m14) {
        if(!flat || m10 == 0f)
            return localZ;
        return (localZ - m14) / m10;
    }

    /**
     * Rewrite a gob-local billboard so {@code obj2view} lands on the visual plane.
     * No-op when flat world is off or the location has no extra Z.
     */
    public static Coord3f flattenBillboard(Coord3f local, Pipe state) {
        if(!isEnabled() || local == null || state == null)
            return local;
        Location.Chain loc = state.get(Homo3D.loc);
        if(loc == null)
            return local;
        Matrix4f m = loc.fin(Matrix4f.id);
        float z = flattenBillboardLocalZ(true, local.z, m.get(2, 2), m.get(3, 2));
        if(z == local.z)
            return local;
        return new Coord3f(local.x, local.y, z);
    }

    public static void toggle() {
        set(!isEnabled());
    }

    /** Stores the setting and rebuilds the world, if the value actually changed. */
    public static void set(boolean val) {
        if(isEnabled() == val)
            return;
        NConfig.set(NConfig.Key.flatsurface, val);
        // Held in lockstep with the live key so the load-time copy in NConfig.read() is a no-op
        // for configs this build writes, while still migrating a change an older build staged.
        NConfig.set(NConfig.Key.nextflatsurface, val);
        apply();
    }

    /**
     * Drops the terrain geometry of every open session so it rebuilds at the new height. Sessions
     * share one config, so a toggle in one of them has to reach all of the others too.
     */
    public static void apply() {
        for(SessionContext ctx : SessionManager.getInstance().getAllSessions()) {
            NGameUI gui = ctx.getGameUI();
            if(gui == null || gui.ui == null || gui.ui.sess == null || gui.ui.sess.glob == null)
                continue;
            gui.ui.sess.glob.map.invalidateAll();
        }
    }
}
