package nurgling.hotkeys;

import nurgling.i18n.L10n;

import java.util.Locale;

/** Display categories used by the unified hotkey settings page. */
public enum HotkeyCategory {
    ALL("hotkey.category.all", "All", "Все"),
    WINDOWS("hotkey.category.windows", "Windows", "Окна"),
    WORLD("hotkey.category.world", "World", "Мир"),
    INVENTORY("hotkey.category.inventory", "Inventory", "Инвентарь"),
    MAP("hotkey.category.map", "Map", "Карта"),
    CRAFTING("hotkey.category.crafting", "Crafting", "Крафт"),
    COMBAT("hotkey.category.combat", "Combat", "Бой"),
    ACTION_MENU("hotkey.category.action_menu", "Action menu", "Меню действий"),
    BELTS("hotkey.category.belts", "Belts", "Панели"),
    SESSIONS("hotkey.category.sessions", "Sessions", "Сессии"),
    AUTOMATION("hotkey.category.automation", "Automation", "Автоматизация");

    private final String labelKey;
    private final String englishLabel;
    private final String russianLabel;

    HotkeyCategory(String labelKey, String englishLabel, String russianLabel) {
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
