package nurgling.hotkeys;

import nurgling.i18n.L10n;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable metadata for one editable hotkey action. */
public final class HotkeyAction {
    private final String id;
    private final String labelKey;
    private final String literalLabel;
    private final HotkeyCategory category;
    private final Set<HotkeyContext> contexts;
    private final Set<InputGesture.Type> allowedTypes;
    private final HotkeyBinding binding;
    private final Integer canonicalMods;
    private final int order;
    private final boolean dynamic;

    public HotkeyAction(String id, String labelKey, String literalLabel,
                        HotkeyCategory category, Set<HotkeyContext> contexts,
                        Set<InputGesture.Type> allowedTypes,
                        HotkeyBinding binding, Integer canonicalMods,
                        int order, boolean dynamic) {
        if(id == null || id.length() == 0 || category == null || contexts == null ||
                contexts.isEmpty() || allowedTypes == null || allowedTypes.isEmpty() || binding == null)
            throw new NullPointerException();
        if(!id.equals(binding.id()))
            throw new IllegalArgumentException("binding id differs from action id");
        this.id = id;
        this.labelKey = labelKey;
        this.literalLabel = literalLabel;
        this.category = category;
        this.contexts = immutableEnumSet(contexts, HotkeyContext.class);
        this.allowedTypes = immutableEnumSet(allowedTypes, InputGesture.Type.class);
        this.binding = binding;
        this.canonicalMods = canonicalMods;
        this.order = order;
        this.dynamic = dynamic;
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> source, Class<E> type) {
        EnumSet<E> copy = EnumSet.noneOf(type);
        copy.addAll(source);
        return Collections.unmodifiableSet(copy);
    }

    public String id() { return id; }
    public String labelKey() { return labelKey; }
    public String literalLabel() { return literalLabel; }
    public HotkeyCategory category() { return category; }
    public Set<HotkeyContext> contexts() { return contexts; }
    public Set<InputGesture.Type> allowedTypes() { return allowedTypes; }
    public HotkeyBinding binding() { return binding; }
    public InputGesture defaultGesture() { return binding.defaultGesture(); }
    public InputGesture current() { return binding.current(); }
    public Integer canonicalMods() { return canonicalMods; }
    public int order() { return order; }
    public boolean dynamic() { return dynamic; }

    public String label() {
        if(literalLabel != null)
            return literalLabel;
        if(labelKey != null) {
            String localized = L10n.get(labelKey);
            // Support both pass-through and the bracketed fallback used by the
            // current L10n implementation when a key is absent.
            if(localized != null && !localized.equals(labelKey) && !localized.equals("[" + labelKey + "]"))
                return localized;
        }
        return id;
    }

    public boolean allows(InputGesture.Type type) {
        return type == InputGesture.Type.NONE || allowedTypes.contains(type);
    }

    /** Retained legacy defaults: GameUI logout precedes the child area button;
     * layout undo and action search share Ctrl+Z through contextual precedence.
     * Only these original pairs of defaults are shared;
     * assigning either action a different occupied gesture still conflicts. */
    boolean sharesDefaultWith(HotkeyAction other, InputGesture mine, InputGesture theirs) {
        boolean legacyPair = (id.equals("areas") && other.id.equals("instantLogoutKB")) ||
                (id.equals("instantLogoutKB") && other.id.equals("areas")) ||
                (id.equals("scm-srch") && other.id.equals("layout.undo")) ||
                (id.equals("layout.undo") && other.id.equals("scm-srch"));
        return legacyPair && mine.equals(defaultGesture()) && theirs.equals(other.defaultGesture());
    }

    boolean overlapsContext(HotkeyAction other) {
        // Login is a separate root screen from gameplay globals.
        if(contexts.contains(HotkeyContext.GLOBAL) || other.contexts.contains(HotkeyContext.GLOBAL)) {
            Set<HotkeyContext> local = contexts.contains(HotkeyContext.GLOBAL) ? other.contexts : contexts;
            return !local.contains(HotkeyContext.LOGIN);
        }
        for(HotkeyContext context : contexts)
            if(other.contexts.contains(context)) return true;
        return false;
    }

    boolean metadataEquals(HotkeyAction other) {
        return other != null && id.equals(other.id) && Objects.equals(labelKey, other.labelKey) &&
                Objects.equals(literalLabel, other.literalLabel) && category == other.category &&
                contexts.equals(other.contexts) && allowedTypes.equals(other.allowedTypes) &&
                binding == other.binding && Objects.equals(canonicalMods, other.canonicalMods) &&
                order == other.order && dynamic == other.dynamic;
    }
}
