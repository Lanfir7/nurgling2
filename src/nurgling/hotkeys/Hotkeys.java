package nurgling.hotkeys;

/** Entry point for the unified keyboard-action registry. */
public final class Hotkeys {
    public static final String INV = "inv";
    public static final String EQU = "equ";
    public static final String AREAS = "areas";
    public static final String COOKBOOK = "cookbook";
    public static final String CRAFT_ATLAS = "craft-atlas";
    public static final String STORAGE = "storage";
    public static final String CAM_LEFT = "cam-left";
    public static final String MAPWND_PROV = "mapwnd/prov";
    public static final String MAKE_ONE = "make/one";
    public static final String FIGHT_0 = "fgt/0";
    public static final String QUICK_ACTION = "quickaction";
    public static final String MINIMAP_FOG = "mwnd_fog";
    public static final String SESSION_NEXT = "session-next";

    private static final HotkeyRegistry REGISTRY = new HotkeyRegistry();
    private static boolean initialized;

    private Hotkeys() {
    }

    /** Return the process-wide registry, populated with the core catalog lazily. */
    public static synchronized HotkeyRegistry registry() {
        if(!initialized) {
            initialized = true;
            HotkeyCatalog.registerCore(REGISTRY);
        }
        return REGISTRY;
    }
}
