package nurgling.widgets.nsettings;

import nurgling.hotkeys.HotkeyAction;
import nurgling.hotkeys.HotkeyCategory;
import nurgling.hotkeys.HotkeyContext;
import nurgling.hotkeys.HotkeyDraftModel;
import nurgling.hotkeys.HotkeyRegistry;
import nurgling.i18n.L10n;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pure state and filtering model for the hotkey settings page. */
public final class HotkeySettingsModel {
    private final HotkeyRegistry registry;
    private final HotkeyDraftModel draft;
    private HotkeyCategory selectedCategory = HotkeyCategory.ALL;
    private String query = "";
    private boolean conflictsOnly;

    public HotkeySettingsModel(HotkeyRegistry registry, HotkeyDraftModel draft) {
        if(registry == null)
            throw new NullPointerException("registry");
        if(draft == null)
            throw new NullPointerException("draft");
        this.registry = registry;
        this.draft = draft;
    }

    public HotkeySettingsModel(HotkeyRegistry registry) {
        this(registry, new HotkeyDraftModel(registry));
    }

    public HotkeyRegistry registry() { return registry; }
    public HotkeyDraftModel draft() { return draft; }

    public void selectCategory(HotkeyCategory category) {
        if(category == null)
            throw new NullPointerException("category");
        selectedCategory = category;
    }

    public HotkeyCategory selectedCategory() { return selectedCategory; }

    public void setQuery(String query) {
        this.query = query == null ? "" : query.trim();
    }

    public String query() { return query; }

    public void setConflictsOnly(boolean conflictsOnly) {
        this.conflictsOnly = conflictsOnly;
    }

    public boolean conflictsOnly() { return conflictsOnly; }

    public List<HotkeyAction> visibleActions() {
        String needle = query.toLowerCase(Locale.ROOT);
        List<HotkeyAction> result = new ArrayList<>();
        for(HotkeyAction action : registry.snapshot()) {
            if(needle.length() == 0 && selectedCategory != HotkeyCategory.ALL &&
                    action.category() != selectedCategory)
                continue;
            if(needle.length() != 0 && !matches(action, needle))
                continue;
            if(conflictsOnly && draft.conflicts(action.id()).isEmpty())
                continue;
            result.add(action);
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean matches(HotkeyAction action, String needle) {
        if(contains(action.id(), needle) || contains(action.label(), needle) ||
                contains(action.labelKey(), needle) || contains(action.category().name(), needle) ||
                contains(displayName(action.category()), needle) ||
                contains(localizedCategory(action.category()), needle))
            return true;
        for(HotkeyContext context : action.contexts())
            if(contains(context.name(), needle) || contains(displayName(context), needle) ||
                    contains(localizedContext(context), needle))
                return true;
        return false;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String displayName(Enum<?> value) {
        String name = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(name.length());
        boolean upper = true;
        for(int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if(upper && c != ' ') {
                result.append(Character.toUpperCase(c));
                upper = false;
            } else {
                result.append(c);
                upper = c == ' ';
            }
        }
        return result.toString();
    }

    private static String localizedCategory(HotkeyCategory category) {
        return localized("hotkeys.category." + category.name().toLowerCase(Locale.ROOT),
                "nsettings.hotkey.category." + category.name().toLowerCase(Locale.ROOT));
    }

    private static String localizedContext(HotkeyContext context) {
        return localized("hotkeys.context." + context.name().toLowerCase(Locale.ROOT),
                "nsettings.hotkey.context." + context.name().toLowerCase(Locale.ROOT));
    }

    private static String localized(String first, String second) {
        String value = L10n.get(first);
        if(value != null && !value.equals(first) && !value.equals("[" + first + "]"))
            return value;
        value = L10n.get(second);
        return value != null && !value.equals(second) && !value.equals("[" + second + "]") ? value : null;
    }
}
