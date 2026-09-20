package nurgling.hotkeys;

import nurgling.i18n.L10n;

/** Event-delivery surfaces used to determine whether two actions overlap. */
public enum HotkeyContext {
    GLOBAL("hotkey.context.global"),
    WORLD_CAMERA("hotkey.context.world_camera"),
    WORLD_PLACEMENT("hotkey.context.world_placement"),
    MAP_WINDOW("hotkey.context.map_window"),
    CHAT_ENTRY("hotkey.context.chat_entry"),
    LOGIN("hotkey.context.login"),
    STACK_INVENTORY("hotkey.context.stack_inventory"),
    STOCKPILE("hotkey.context.stockpile"),
    WORLD_SURFACE("hotkey.context.world_surface"),
    MAP_SURFACE("hotkey.context.map_surface"),
    MINIMAP_SURFACE("hotkey.context.minimap_surface"),
    INVENTORY_ITEM_GENERIC("hotkey.context.inventory_item_generic"),
    INVENTORY_ITEM_NURGLING("hotkey.context.inventory_item_nurgling"),
    INVENTORY_BACKGROUND("hotkey.context.inventory_background"),
    HELD_ITEM("hotkey.context.held_item"),
    CRAFT_WINDOW("hotkey.context.craft_window"),
    COMBAT_UI("hotkey.context.combat_ui"),
    FLOWER_MENU_MODE("hotkey.context.flower_menu_mode"),
    MENU_SEARCH_MODE("hotkey.context.menu_search_mode"),
    ROSTER_BUTTON_MODE("hotkey.context.roster_button_mode"),
    BUDDY_WINDOW("hotkey.context.buddy_window"),
    WOUND_WINDOW("hotkey.context.wound_window"),
    MASTER_MINER_WINDOW("hotkey.context.master_miner_window"),
    LAYOUT_EDIT("hotkey.context.layout_edit"),
    COMPASS_WIDGET("hotkey.context.compass_widget"),
    LAND_SURVEY("hotkey.context.land_survey"),
    RESOURCE_TIMERS_WINDOW("hotkey.context.resource_timers_window"),
    MAP_ICON_SETTINGS("hotkey.context.map_icon_settings"),
    ACTION_MENU("hotkey.context.action_menu"),
    BELT("hotkey.context.belt"),
    SESSION_SWITCHER("hotkey.context.session_switcher");

    private final String labelKey;

    HotkeyContext(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() { return labelKey; }

    public String label() {
        return L10n.hasKey(labelKey) ? L10n.get(labelKey) : name();
    }
}
