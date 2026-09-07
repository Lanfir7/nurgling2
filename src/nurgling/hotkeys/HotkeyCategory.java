package nurgling.hotkeys;

import nurgling.i18n.L10n;

/** Display categories used by the unified hotkey settings page. */
public enum HotkeyCategory {
    ALL("hotkey.category.all"),
    WINDOWS("hotkey.category.windows"),
    WORLD("hotkey.category.world"),
    INVENTORY("hotkey.category.inventory"),
    MAP("hotkey.category.map"),
    CRAFTING("hotkey.category.crafting"),
    COMBAT("hotkey.category.combat"),
    ACTION_MENU("hotkey.category.action_menu"),
    BELTS("hotkey.category.belts"),
    SESSIONS("hotkey.category.sessions"),
    AUTOMATION("hotkey.category.automation");

    private final String labelKey;

    HotkeyCategory(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() { return labelKey; }

    public String label() {
        return L10n.hasKey(labelKey) ? L10n.get(labelKey) : name();
    }
}
