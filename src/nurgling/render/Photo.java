package nurgling.render;

/*
 * Photo mode (Vulkan only): the interface is hidden, a click on the
 * scene sets the focus, and what is nearer or further than that goes
 * soft (see SceneFX.DoF). Shift and the mouse wheel set how soft.
 */
public class Photo {
    private static volatile Object owner = null;
    private static volatile Runnable restore = null;

    public static boolean active(Object view) {
        return (view != null) && (owner == view);
    }

    public static void enter(Object view, Runnable restoreOwner) {
        if(view == null || restoreOwner == null)
            throw new IllegalArgumentException("Photo mode requires a view and restore action");
        stop();
        owner = view;
        restore = restoreOwner;
        focus = 0;
        since = haven.Utils.rtime();
    }

    public static void leave(Object view) {
        if(active(view)) {
            owner = null;
            restore = null;
        }
    }

    public static void stop() {
        Object oldOwner = owner;
        Runnable old = restore;
        if(old != null)
            old.run();
        if(owner == oldOwner) {
            owner = null;
            restore = null;
        }
    }
    /* View distance in focus (0 until clicked: everything sharp),
     * and how strong the blur is. */
    public static volatile float focus = 0, aperture = 1.0f;
    public static volatile double since = 0;
}
