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
    public static final String ITEM_TAKE = "item.take";
    public static final String ITEM_INTERACT = "item.interact";
    public static final String ITEM_TRANSFER_ONE = "item.transfer.one";
    public static final String ITEM_TRANSFER_ALL = "item.transfer.all";
    public static final String ITEM_DROP_ONE = "item.drop.one";
    public static final String ITEM_DROP_ALL = "item.drop.all";
    public static final String ITEM_RECIPES = "item.recipes";
    public static final String ITEM_TRANSFER_SAME_DESC = "item.transfer_same.desc";
    public static final String ITEM_TRANSFER_SAME_ASC = "item.transfer_same.asc";
    public static final String ITEM_DROP_SAME_DESC = "item.drop_same.desc";
    public static final String ITEM_DROP_SAME_ASC = "item.drop_same.asc";
    public static final String INVENTORY_TRANSFER_TO_MAIN = "inventory.transfer_to_main";
    public static final String INVENTORY_TRANSFER_FROM_MAIN = "inventory.transfer_from_main";
    public static final String HELD_DROP_ON_TARGET = "held.drop_on_target";
    public static final String HELD_INTERACT_WITH_TARGET = "held.interact_with_target";
    public static final String HELD_OPEN_WITHOUT_USING = "held.open_without_using";
    public static final String HELD_LIGHT_FROM_FIRE = "held.light_from_fire";

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
