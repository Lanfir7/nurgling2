package nurgling.hotkeys;

import nurgling.i18n.L10n;

import java.util.Locale;

/** Event-delivery surfaces used to determine whether two actions overlap. */
public enum HotkeyContext {
    GLOBAL("hotkey.context.global", "Global", "Глобальный"),
    WORLD_SURFACE("hotkey.context.world_surface", "World surface", "Мировая поверхность"),
    MAP_SURFACE("hotkey.context.map_surface", "Map surface", "Поверхность карты"),
    MINIMAP_SURFACE("hotkey.context.minimap_surface", "Minimap", "Миникарта"),
    INVENTORY_ITEM_GENERIC("hotkey.context.inventory_item_generic", "Inventory item", "Предмет инвентаря"),
    INVENTORY_ITEM_NURGLING("hotkey.context.inventory_item_nurgling", "Nurgling inventory item", "Предмет инвентаря Нёрлинга"),
    INVENTORY_BACKGROUND("hotkey.context.inventory_background", "Inventory background", "Фон инвентаря"),
    HELD_ITEM("hotkey.context.held_item", "Held item", "Предмет в руке"),
    CRAFT_WINDOW("hotkey.context.craft_window", "Craft window", "Окно крафта"),
    COMBAT_UI("hotkey.context.combat_ui", "Combat UI", "Боевой интерфейс"),
    FLOWER_MENU_MODE("hotkey.context.flower_menu_mode", "Flower menu", "Меню цветка"),
    MENU_SEARCH_MODE("hotkey.context.menu_search_mode", "Menu search", "Поиск по меню"),
    ROSTER_BUTTON_MODE("hotkey.context.roster_button_mode", "Roster button", "Кнопка списка"),
    BUDDY_WINDOW("hotkey.context.buddy_window", "Buddy window", "Окно друзей"),
    WOUND_WINDOW("hotkey.context.wound_window", "Wound window", "Окно ран"),
    LAYOUT_EDIT("hotkey.context.layout_edit", "Layout edit", "Редактирование раскладки"),
    COMPASS_WIDGET("hotkey.context.compass_widget", "Compass widget", "Виджет компаса"),
    LAND_SURVEY("hotkey.context.land_survey", "Land survey", "Землемер"),
    RESOURCE_TIMERS_WINDOW("hotkey.context.resource_timers_window", "Resource timers", "Таймеры ресурсов"),
    MAP_ICON_SETTINGS("hotkey.context.map_icon_settings", "Map icon settings", "Настройки иконок карты"),
    ACTION_MENU("hotkey.context.action_menu", "Action menu", "Меню действий"),
    BELT("hotkey.context.belt", "Belt", "Панель"),
    SESSION_SWITCHER("hotkey.context.session_switcher", "Session switcher", "Переключатель сессий");

    private final String labelKey;
    private final String englishLabel;
    private final String russianLabel;

    HotkeyContext(String labelKey, String englishLabel, String russianLabel) {
        this.labelKey = labelKey;
        this.englishLabel = englishLabel;
        this.russianLabel = russianLabel;
    }

    public String labelKey() { return labelKey; }
    public String englishLabel() { return englishLabel; }
    public String russianLabel() { return russianLabel; }

    public String label() {
        return L10n.hasKey(labelKey) ? L10n.get(labelKey) : englishLabel;
    }

    public String label(Locale locale) {
        return locale != null && "ru".equals(locale.getLanguage()) ? russianLabel : englishLabel;
    }
}
